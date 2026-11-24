package com.flightlog.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void unexpectedErrorReturns500WithoutLeakingDetails() {
    ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("secret-internal"));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(problem.getTitle()).isEqualTo("服务器错误");
    assertThat(problem.getDetail()).isEqualTo("服务器内部错误，请稍后再试");
    assertThat(problem.toString()).doesNotContain("secret-internal");
  }
}
