package com.flightlog.common;

import org.springframework.http.HttpStatus;

/** 业务错误：带 HTTP 状态和可以直接显示给用户的中文信息，由 {@link GlobalExceptionHandler} 转成 ProblemDetail。 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;

  public ApiException(HttpStatus status, String detail) {
    super(detail);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public static ApiException badRequest(String detail) {
    return new ApiException(HttpStatus.BAD_REQUEST, detail);
  }

  public static ApiException notFound(String detail) {
    return new ApiException(HttpStatus.NOT_FOUND, detail);
  }

  public static ApiException conflict(String detail) {
    return new ApiException(HttpStatus.CONFLICT, detail);
  }

  public static ApiException unauthorized(String detail) {
    return new ApiException(HttpStatus.UNAUTHORIZED, detail);
  }
}
