package com.flightlog.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import com.flightlog.auth.SessionClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 没有配置 key 时：应用照常运行，查询功能明确告知不可用。 */
@SpringBootTest
@AutoConfigureMockMvc
class LookupDisabledTest extends SqliteTestSupport {

  private final MockMvcTester mvc;

  LookupDisabledTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  @Test
  void configShowsFeaturesDisabledWithoutLogin() {
    MvcTestResult result = new SessionClient(mvc).get("/api/config");
    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.lookupEnabled").isEqualTo(false);
    assertThat(result).bodyJson().extractingPath("$.aiEnabled").isEqualTo(false);
  }

  @Test
  void aiParseReturnsServiceUnavailable() {
    SessionClient user = new SessionClient(mvc);
    user.register("ai" + System.nanoTime() + "@example.com", "correct-horse-1", null);

    MvcTestResult result = user.postJson("/api/ai/parse", "{\"text\":\"NH242\"}");
    assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("没有配置 Gemini API key，AI 解析不可用，请手动填写");
  }

  @Test
  void lookupReturnsServiceUnavailable() {
    SessionClient user = new SessionClient(mvc);
    user.register("d" + System.nanoTime() + "@example.com", "correct-horse-1", null);

    MvcTestResult result = user.get("/api/lookup/flight?number=NH242&date=2026-11-03");
    assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("没有配置 AeroDataBox API key，航班号查询不可用，请手动填写");
  }
}
