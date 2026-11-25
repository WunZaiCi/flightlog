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

/** 登录频率限制：同一 IP 连续失败 5 次后锁定 15 分钟。经 Tailscale 访问时按 X-Forwarded-For 识别 IP。 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitApiTest extends SqliteTestSupport {

  private static final String PASSWORD = "correct-horse-1";

  private final MockMvcTester mvc;

  LoginRateLimitApiTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  private SessionClient from(String ip) {
    return new SessionClient(mvc).withHeader("X-Forwarded-For", ip);
  }

  @Test
  void fiveFailuresLockTheIpEvenForTheRightPassword() {
    String email = "rl" + System.nanoTime() + "@example.com";
    from("10.0.0.1").register(email, PASSWORD, null);
    SessionClient attacker = from("10.0.0.2");

    for (int i = 0; i < 5; i++) {
      assertThat(attacker.login(email, "wrong-password")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
    MvcTestResult locked = attacker.login(email, PASSWORD);
    assertThat(locked).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(locked).bodyJson().extractingPath("$.detail").isEqualTo("登录失败次数过多，请 15 分钟后再试");

    // 其他 IP 不受影响
    assertThat(from("10.0.0.3").login(email, PASSWORD)).hasStatusOk();
  }

  @Test
  void successfulLoginResetsTheCounter() {
    String email = "ok" + System.nanoTime() + "@example.com";
    from("10.0.1.1").register(email, PASSWORD, null);
    SessionClient user = from("10.0.1.2");

    for (int i = 0; i < 4; i++) {
      user.login(email, "wrong-password");
    }
    assertThat(user.login(email, PASSWORD)).hasStatusOk();
    for (int i = 0; i < 4; i++) {
      user.login(email, "wrong-password");
    }
    assertThat(user.login(email, PASSWORD)).hasStatusOk();
  }
}
