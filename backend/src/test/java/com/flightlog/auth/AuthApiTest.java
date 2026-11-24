package com.flightlog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest extends SqliteTestSupport {

  private static final String PASSWORD = "correct-horse-1";

  private final MockMvcTester mvc;
  private final JdbcClient jdbc;

  AuthApiTest(@Autowired MockMvcTester mvc, @Autowired JdbcClient jdbc) {
    this.mvc = mvc;
    this.jdbc = jdbc;
  }

  private SessionClient client() {
    return new SessionClient(mvc);
  }

  private static String uniqueEmail(String prefix) {
    return prefix + "-" + System.nanoTime() + "@example.com";
  }

  // ---- 登录门禁 ----

  @Test
  void unauthenticatedApiReturns401ProblemInChinese() {
    MvcTestResult result = client().get("/api/auth/me");
    assertThat(result)
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("请先登录");
  }

  @Test
  void unknownApiPathAlsoRequiresLogin() {
    assertThat(client().get("/api/flights")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void healthAndStaticPagesArePublic() {
    assertThat(client().get("/api/health")).hasStatusOk();
    assertThat(client().get("/")).hasStatusOk();
  }

  // ---- CSRF ----

  @Test
  void firstRequestReceivesReadableCsrfCookie() {
    SessionClient browser = client().primeCsrf();
    assertThat(browser.cookie(SessionClient.CSRF_COOKIE)).isNotBlank();
    assertThat(browser.lastSetCookieHeader(SessionClient.CSRF_COOKIE))
        .doesNotContainIgnoringCase("HttpOnly");
  }

  @Test
  void writeWithoutCsrfTokenIsRejectedInChinese() {
    MvcTestResult result =
        client()
            .primeCsrf()
            .postJsonWithoutCsrf(
                "/api/auth/login", "{\"email\":\"a@example.com\",\"password\":\"x\"}");
    assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("页面已过期，请刷新后重试");
  }

  // ---- 注册 ----

  @Test
  void registerNormalizesEmailLogsInAndMeReturnsUser() {
    String email = uniqueEmail("Alice").toLowerCase();
    SessionClient browser = client();

    MvcTestResult registered = browser.register("  " + email.toUpperCase() + " ", PASSWORD, "爱丽丝");
    assertThat(registered).hasStatus(HttpStatus.CREATED);
    assertThat(registered).bodyJson().extractingPath("$.email").isEqualTo(email);
    assertThat(registered).bodyJson().extractingPath("$.displayName").isEqualTo("爱丽丝");
    assertThat(registered).bodyJson().doesNotHavePath("$.passwordHash");

    assertThat(browser.cookie(SessionClient.SESSION_COOKIE)).isNotBlank();
    MvcTestResult me = browser.get("/api/auth/me");
    assertThat(me).hasStatusOk();
    assertThat(me).bodyJson().extractingPath("$.email").isEqualTo(email);
  }

  @Test
  void displayNameDefaultsToEmailLocalPart() {
    String email = uniqueEmail("bob");
    MvcTestResult result = client().register(email, PASSWORD, null);
    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.displayName")
        .isEqualTo(email.substring(0, email.indexOf('@')));
  }

  @Test
  void duplicateEmailIsRejectedCaseInsensitively() {
    String email = uniqueEmail("dup");
    assertThat(client().register(email, PASSWORD, "A")).hasStatus(HttpStatus.CREATED);

    MvcTestResult again = client().register(email.toUpperCase(), PASSWORD, "B");
    assertThat(again).hasStatus(HttpStatus.CONFLICT);
    assertThat(again).bodyJson().extractingPath("$.detail").isEqualTo("该邮箱已注册");
  }

  @Test
  void invalidRegistrationReturnsFieldErrorsInChinese() {
    MvcTestResult result = client().register("not-an-email", "short", "A");
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.errors.email").isEqualTo("邮箱格式不正确");
    assertThat(result).bodyJson().extractingPath("$.errors.password").isEqualTo("密码长度为 8 到 64 个字符");
  }

  @Test
  void passwordIsStoredAsBcryptHash() {
    String email = uniqueEmail("hash");
    client().register(email, PASSWORD, "H");

    String hash =
        jdbc.sql("SELECT password_hash FROM users WHERE email = :email")
            .param("email", email)
            .query(String.class)
            .single();
    assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
  }

  // ---- 登录 ----

  @Test
  void loginWithWrongPasswordOrUnknownEmailIsRejectedTheSameWay() {
    String email = uniqueEmail("carol");
    client().register(email, PASSWORD, "C");

    for (MvcTestResult result :
        List.of(
            client().login(email, "wrong-password"),
            client().login(uniqueEmail("nobody"), PASSWORD))) {
      assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
      assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("邮箱或密码错误");
    }
  }

  @Test
  void loginCreatesSessionStoredInDatabase() {
    String email = uniqueEmail("dave");
    client().register(email, PASSWORD, "D");

    SessionClient browser = client();
    MvcTestResult login = browser.login(email.toUpperCase(), PASSWORD);
    assertThat(login).hasStatusOk();
    assertThat(login).bodyJson().extractingPath("$.email").isEqualTo(email);
    assertThat(browser.get("/api/auth/me")).hasStatusOk();

    Integer sessions =
        jdbc.sql("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = :email")
            .param("email", email)
            .query(Integer.class)
            .single();
    assertThat(sessions).isPositive();
  }

  @Test
  void loginRotatesSessionId() {
    String email = uniqueEmail("erin");
    client().register(email, PASSWORD, "E");

    SessionClient browser = client();
    browser.login(email, PASSWORD);
    String first = browser.cookie(SessionClient.SESSION_COOKIE);
    browser.login(email, PASSWORD);
    String second = browser.cookie(SessionClient.SESSION_COOKIE);

    assertThat(first).isNotBlank();
    assertThat(second).isNotBlank().isNotEqualTo(first);
  }

  // ---- 登出 ----

  @Test
  void logoutInvalidatesSessionServerSide() {
    String email = uniqueEmail("frank");
    SessionClient browser = client();
    browser.register(email, PASSWORD, "F");
    String oldSession = browser.cookie(SessionClient.SESSION_COOKIE);

    assertThat(browser.post("/api/auth/logout")).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(browser.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);

    // 即使有人保留了旧的会话 Cookie，也已经失效
    SessionClient replay = client();
    replay.setCookie(SessionClient.SESSION_COOKIE, oldSession);
    assertThat(replay.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  // ---- 用户隔离 ----

  @Test
  void eachUserOnlySeesThemselves() {
    String a = uniqueEmail("usera");
    String b = uniqueEmail("userb");
    SessionClient alice = client();
    SessionClient bob = client();
    alice.register(a, PASSWORD, "A");
    bob.register(b, PASSWORD, "B");

    assertThat(alice.get("/api/auth/me")).bodyJson().extractingPath("$.email").isEqualTo(a);
    assertThat(bob.get("/api/auth/me")).bodyJson().extractingPath("$.email").isEqualTo(b);
  }
}
