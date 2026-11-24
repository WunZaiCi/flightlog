package com.flightlog.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 业务错误：带 HTTP 状态和可以直接显示给用户的中文信息，由 {@link GlobalExceptionHandler} 转成 ProblemDetail。
 *
 * <p>{@code fieldErrors} 会放在 ProblemDetail 的 {@code errors} 里（字段 → 中文信息），前端显示在对应输入框下； {@code
 * properties} 是其他附加信息（例如重复航班的 ID）。
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final Map<String, String> fieldErrors;
  private final Map<String, Object> properties;

  public ApiException(HttpStatus status, String detail) {
    this(status, detail, Map.of(), Map.of());
  }

  public ApiException(
      HttpStatus status,
      String detail,
      Map<String, String> fieldErrors,
      Map<String, Object> properties) {
    super(detail);
    this.status = status;
    this.fieldErrors = Map.copyOf(fieldErrors);
    this.properties = Map.copyOf(properties);
  }

  public HttpStatus status() {
    return status;
  }

  public Map<String, String> fieldErrors() {
    return fieldErrors;
  }

  public Map<String, Object> properties() {
    return properties;
  }

  public static ApiException badRequest(String detail) {
    return new ApiException(HttpStatus.BAD_REQUEST, detail);
  }

  /** 单个字段的校验错误。 */
  public static ApiException invalidField(String field, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message, Map.of(field, message), Map.of());
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
