package com.flightlog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 修改密码：POST /api/auth/password。 */
@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordApiTest extends SqliteTestSupport {

  private static final String PASSWORD = "correct-horse-1";
  private static final String NEW_PASSWORD = "battery-staple-2";

  private final MockMvcTester mvc;

  ChangePasswordApiTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  /** 每个测试用不同的 IP，互不影响登录频率限制。 */
  private SessionClient from(String ip) {
    return new SessionClient(mvc).withHeader("X-Forwarded-For", ip);
  }

  private static String uniqueEmail() {
    return "pw" + System.nanoTime() + "@example.com";
  }

  private static String body(String current, String next) {
    return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
  }

  @Test
  void changesPasswordSoOnlyTheNewOneWorks() {
    String email = uniqueEmail();
    SessionClient user = from("10.1.0.1");
    user.register(email, PASSWORD, null);

    assertThat(user.postJson("/api/auth/password", body(PASSWORD, NEW_PASSWORD)))
        .hasStatus(HttpStatus.NO_CONTENT);

    // 当前设备保持登录
    assertThat(user.get("/api/auth/me")).hasStatusOk();
    assertThat(from("10.1.0.2").login(email, PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(from("10.1.0.3").login(email, NEW_PASSWORD)).hasStatusOk();
  }

  @Test
  void logsOutOtherDevicesButKeepsTheCurrentOne() {
    String email = uniqueEmail();
    SessionClient phone = from("10.1.1.1");
    phone.register(email, PASSWORD, null);
    SessionClient laptop = from("10.1.1.2");
    assertThat(laptop.login(email, PASSWORD)).hasStatusOk();

    assertThat(phone.postJson("/api/auth/password", body(PASSWORD, NEW_PASSWORD)))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(phone.get("/api/auth/me")).hasStatusOk();
    assertThat(laptop.get("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void wrongCurrentPasswordIsAFieldError() {
    SessionClient user = from("10.1.2.1");
    user.register(uniqueEmail(), PASSWORD, null);

    MvcTestResult result =
        user.postJson("/api/auth/password", body("wrong-password", NEW_PASSWORD));
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.errors.currentPassword").isEqualTo("当前密码不正确");
  }

  @Test
  void newPasswordFollowsTheRegistrationRules() {
    SessionClient user = from("10.1.3.1");
    user.register(uniqueEmail(), PASSWORD, null);

    MvcTestResult tooShort = user.postJson("/api/auth/password", body(PASSWORD, "short"));
    assertThat(tooShort).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(tooShort)
        .bodyJson()
        .extractingPath("$.errors.newPassword")
        .isEqualTo("密码长度为 8 到 64 个字符");

    MvcTestResult same = user.postJson("/api/auth/password", body(PASSWORD, PASSWORD));
    assertThat(same).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(same).bodyJson().extractingPath("$.errors.newPassword").isEqualTo("新密码不能和当前密码相同");

    String tooManyBytes = "密".repeat(25); // 25 个字符，75 个字节，超过 bcrypt 的 72 字节
    MvcTestResult tooLong = user.postJson("/api/auth/password", body(PASSWORD, tooManyBytes));
    assertThat(tooLong).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(tooLong)
        .bodyJson()
        .extractingPath("$.errors.newPassword")
        .isEqualTo("密码太长，请减少中文或特殊字符");
  }

  @Test
  void wrongCurrentPasswordCountsTowardsTheLoginLock() {
    SessionClient user = from("10.1.4.1");
    user.register(uniqueEmail(), PASSWORD, null);

    for (int i = 0; i < 5; i++) {
      assertThat(user.postJson("/api/auth/password", body("wrong-password", NEW_PASSWORD)))
          .hasStatus(HttpStatus.BAD_REQUEST);
    }
    MvcTestResult locked = user.postJson("/api/auth/password", body(PASSWORD, NEW_PASSWORD));
    assertThat(locked).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(locked).bodyJson().extractingPath("$.detail").isEqualTo("密码输错次数过多，请 15 分钟后再试");
  }

  @Test
  void requiresLoginAndCsrf() {
    SessionClient anonymous = from("10.1.5.1").primeCsrf();
    assertThat(anonymous.postJson("/api/auth/password", body(PASSWORD, NEW_PASSWORD)))
        .hasStatus(HttpStatus.UNAUTHORIZED);

    SessionClient user = from("10.1.5.2");
    user.register(uniqueEmail(), PASSWORD, null);
    assertThat(user.postJsonWithoutCsrf("/api/auth/password", body(PASSWORD, NEW_PASSWORD)))
        .hasStatus(HttpStatus.FORBIDDEN);
  }
}
