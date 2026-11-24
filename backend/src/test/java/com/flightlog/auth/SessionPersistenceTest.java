package com.flightlog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.FlightlogApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/** 会话存在 SQLite 里：应用（容器）重启后，旧的会话 Cookie 仍然有效，用户不需要重新登录。 */
class SessionPersistenceTest {

  @TempDir Path dataDir;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void sessionSurvivesRestart() throws Exception {
    String sessionCookie;
    try (ConfigurableApplicationContext app = start()) {
      String base = baseUrl(app);
      HttpResponse<String> health = send(HttpRequest.newBuilder(URI.create(base + "/api/health")));
      String csrf = cookie(health, "XSRF-TOKEN");

      HttpResponse<String> registered =
          send(
              HttpRequest.newBuilder(URI.create(base + "/api/auth/register"))
                  .header("Content-Type", "application/json")
                  .header("X-XSRF-TOKEN", csrf)
                  .header("Cookie", "XSRF-TOKEN=" + csrf)
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"email\":\"restart@example.com\",\"password\":\"correct-horse-1\"}")));
      assertThat(registered.statusCode()).isEqualTo(201);
      sessionCookie = cookie(registered, "FLIGHTLOG_SESSION");
    }

    try (ConfigurableApplicationContext app = start()) {
      HttpResponse<String> me =
          send(
              HttpRequest.newBuilder(URI.create(baseUrl(app) + "/api/auth/me"))
                  .header("Cookie", "FLIGHTLOG_SESSION=" + sessionCookie));
      assertThat(me.statusCode()).isEqualTo(200);
      assertThat(me.body()).contains("restart@example.com");
    }
  }

  private ConfigurableApplicationContext start() {
    return new SpringApplicationBuilder(FlightlogApplication.class)
        .run(
            "--flightlog.data-dir=" + dataDir.toAbsolutePath(),
            "--server.port=0",
            "--spring.main.banner-mode=off");
  }

  private static String baseUrl(ConfigurableApplicationContext app) {
    int port = ((WebServerApplicationContext) app).getWebServer().getPort();
    return "http://localhost:" + port;
  }

  private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String cookie(HttpResponse<String> response, String name) {
    return response.headers().allValues("Set-Cookie").stream()
        .filter(c -> c.startsWith(name + "="))
        .map(c -> c.substring(name.length() + 1, c.indexOf(';')))
        .findFirst()
        .orElseThrow(() -> new AssertionError("缺少 Cookie " + name));
  }
}
