package com.flightlog.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest extends SqliteTestSupport {

  private final MockMvcTester mvc;

  HealthControllerTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  @Test
  void healthReturnsOk() {
    assertThat(mvc.get().uri("/api/health"))
        .hasStatusOk()
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isLenientlyEqualTo("{\"status\":\"ok\",\"database\":\"ok\"}");
  }
}
