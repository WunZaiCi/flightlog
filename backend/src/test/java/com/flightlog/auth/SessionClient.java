package com.flightlog.auth;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 模拟一个浏览器：保存 Set-Cookie（会话 Cookie 和 XSRF-TOKEN），后续请求自动带上；写请求自动加 X-XSRF-TOKEN 头。
 *
 * <p>Spring Session 通过 Set-Cookie 头写 Cookie，MockMvc 的 getCookies() 看不到，所以这里自己解析。
 */
public class SessionClient {

  /**
   * MockMvc 没有内嵌服务器，Spring Boot 会从模拟的 ServletContext 读取会话 Cookie 配置，Cookie 名是默认的 SESSION。
   * 真实服务器上的名字和属性（FLIGHTLOG_SESSION、HttpOnly、SameSite）由 SessionCookieHttpTest 检查。
   */
  public static final String SESSION_COOKIE = "SESSION";

  public static final String CSRF_COOKIE = "XSRF-TOKEN";

  private final MockMvcTester mvc;
  private final Map<String, String> cookies = new LinkedHashMap<>();
  private final Map<String, String> lastSetCookieHeaders = new LinkedHashMap<>();
  private final Map<String, String> headers = new LinkedHashMap<>();

  public SessionClient(MockMvcTester mvc) {
    this.mvc = mvc;
  }

  /** 之后每个请求都带上这个请求头（例如模拟 X-Forwarded-For）。 */
  public SessionClient withHeader(String name, String value) {
    headers.put(name, value);
    return this;
  }

  public MvcTestResult get(String uri) {
    return send(mvc.get().uri(uri), false);
  }

  public MvcTestResult postJson(String uri, String json) {
    return send(mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json), true);
  }

  public MvcTestResult postJsonWithoutCsrf(String uri, String json) {
    return send(mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json), false);
  }

  public MvcTestResult putJson(String uri, String json) {
    return send(mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json), true);
  }

  public MvcTestResult post(String uri) {
    return send(mvc.post().uri(uri), true);
  }

  public MvcTestResult delete(String uri) {
    return send(mvc.delete().uri(uri), true);
  }

  /** 先 GET 一次拿到 CSRF Cookie（前端第一次加载页面时也是这样）。 */
  public SessionClient primeCsrf() {
    get("/api/health");
    return this;
  }

  public MvcTestResult register(String email, String password, String displayName) {
    primeCsrfIfMissing();
    String name = displayName == null ? "null" : quote(displayName);
    return postJson(
        "/api/auth/register",
        "{\"email\":"
            + quote(email)
            + ",\"password\":"
            + quote(password)
            + ",\"displayName\":"
            + name
            + "}");
  }

  public MvcTestResult login(String email, String password) {
    primeCsrfIfMissing();
    return postJson(
        "/api/auth/login", "{\"email\":" + quote(email) + ",\"password\":" + quote(password) + "}");
  }

  public String cookie(String name) {
    return cookies.get(name);
  }

  /** 最近一次响应里某个 Cookie 的完整 Set-Cookie 头（用于检查 HttpOnly、SameSite 等属性）。 */
  public String lastSetCookieHeader(String name) {
    return lastSetCookieHeaders.get(name);
  }

  public void setCookie(String name, String value) {
    cookies.put(name, value);
  }

  private static String quote(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private void primeCsrfIfMissing() {
    if (!cookies.containsKey(CSRF_COOKIE)) {
      primeCsrf();
    }
  }

  private MvcTestResult send(MockMvcRequestBuilder request, boolean withCsrf) {
    // MockMvc 里原始的 Cookie 请求头不会解析成 request.getCookies()，要用 cookie(...) 传
    cookies.forEach((name, value) -> request.cookie(new Cookie(name, value)));
    headers.forEach(request::header);
    if (withCsrf && cookies.containsKey(CSRF_COOKIE)) {
      request.header("X-XSRF-TOKEN", cookies.get(CSRF_COOKIE));
    }
    MvcTestResult result = request.exchange();
    lastSetCookieHeaders.clear();
    for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
      storeCookie(header);
    }
    return result;
  }

  private void storeCookie(String header) {
    String[] parts = header.split(";");
    String[] nameValue = parts[0].split("=", 2);
    String name = nameValue[0].trim();
    String value = nameValue.length > 1 ? nameValue[1].trim() : "";
    lastSetCookieHeaders.put(name, header);
    String lower = header.toLowerCase(Locale.ROOT);
    boolean expired =
        value.isEmpty()
            || lower.contains("max-age=0")
            || lower.contains("expires=thu, 01 jan 1970");
    if (expired) {
      cookies.remove(name);
    } else {
      cookies.put(name, value);
    }
  }
}
