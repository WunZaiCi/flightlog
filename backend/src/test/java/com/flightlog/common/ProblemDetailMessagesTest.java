package com.flightlog.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** messages.properties 里的中文 ProblemDetail 文案要真正生效（防止改名或编码问题后悄悄退回英文）。 */
@SpringBootTest
@AutoConfigureMockMvc
class ProblemDetailMessagesTest extends SqliteTestSupport {

  private final MockMvcTester mvc;

  ProblemDetailMessagesTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  @Test
  void methodNotAllowedIsChinese() {
    var result = mvc.post().uri("/api/health").exchange();
    assertThat(result)
        .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("不支持 POST 请求方法");
    assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("请求方法不允许");
  }

  @Test
  void notFoundTitleIsChinese() {
    var result = mvc.get().uri("/api/nope").exchange();
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("资源不存在");
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("接口不存在");
  }
}
