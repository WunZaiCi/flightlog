package com.flightlog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 在真实的内嵌服务器上检查会话 Cookie 的名字和安全属性（MockMvc 没有内嵌服务器，这些配置不生效）。
 *
 * <p>本机 http 访问不加 Secure；经 Tailscale 的 HTTPS 访问（X-Forwarded-Proto: https）加 Secure。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SessionCookieHttpTest extends SqliteTestSupport {

  @LocalServerPort int port;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void sessionCookieIsHttpOnlyAndSameSiteLaxWithoutSecureOnPlainHttp() throws Exception {
    String sessionCookie = registerAndGetSessionCookie("plain-" + System.nanoTime(), null);

    assertThat(sessionCookie)
        .startsWith("FLIGHTLOG_SESSION=")
        .containsIgnoringCase("HttpOnly")
        .containsIgnoringCase("SameSite=Lax")
        .doesNotContainIgnoringCase("Secure");
  }

  @Test
  void sessionCookieIsSecureWhenForwardedOverHttps() throws Exception {
    String sessionCookie = registerAndGetSessionCookie("tls-" + System.nanoTime(), "https");

    assertThat(sessionCookie)
        .startsWith("FLIGHTLOG_SESSION=")
        .containsIgnoringCase("HttpOnly")
        .containsIgnoringCase("Secure");
  }

  private String registerAndGetSessionCookie(String user, String forwardedProto) throws Exception {
    HttpResponse<String> health = http.send(get("/api/health", forwardedProto), ofString());
    String csrf = cookieValue(health.headers().allValues("Set-Cookie"), "XSRF-TOKEN").orElseThrow();

    HttpRequest.Builder register =
        builder("/api/auth/register", forwardedProto)
            .header("Content-Type", "application/json")
            .header("X-XSRF-TOKEN", csrf)
            .header("Cookie", "XSRF-TOKEN=" + csrf)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"" + user + "@example.com\",\"password\":\"correct-horse-1\"}"));
    HttpResponse<String> response = http.send(register.build(), ofString());
    assertThat(response.statusCode()).isEqualTo(201);

    return response.headers().allValues("Set-Cookie").stream()
        .filter(c -> c.startsWith("FLIGHTLOG_SESSION="))
        .findFirst()
        .orElseThrow(() -> new AssertionError("没有会话 Cookie：" + response.headers()));
  }

  private HttpRequest get(String path, String forwardedProto) {
    return builder(path, forwardedProto).GET().build();
  }

  private HttpRequest.Builder builder(String path, String forwardedProto) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (forwardedProto != null) {
      builder.header("X-Forwarded-Proto", forwardedProto);
    }
    return builder;
  }

  private static HttpResponse.BodyHandler<String> ofString() {
    return HttpResponse.BodyHandlers.ofString();
  }

  private static Optional<String> cookieValue(List<String> setCookies, String name) {
    return setCookies.stream()
        .filter(c -> c.startsWith(name + "="))
        .map(c -> c.substring(name.length() + 1, c.indexOf(';') > 0 ? c.indexOf(';') : c.length()))
        .findFirst();
  }
}
