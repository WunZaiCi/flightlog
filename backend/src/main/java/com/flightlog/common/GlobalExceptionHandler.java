package com.flightlog.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一错误处理，返回 RFC 9457 ProblemDetail。错误信息用中文，前端可直接显示。
 *
 * <p>Spring MVC 内置异常的中文文案放在 {@code messages.properties}（键为 {@code problemDetail.<异常类名>}）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String path = ex.getResourcePath().replaceFirst("^/+", "");
    boolean isApi = path.equals("api") || path.startsWith("api/");
    ex.getBody().setDetail(isApi ? "接口不存在" : "请求的资源不存在");
    return handleExceptionInternal(ex, ex.getBody(), headers, status, request);
  }

  /** 兜底：未预料的异常只记日志，不把内部信息返回给客户端。 */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("未处理的异常", ex);
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后再试");
  }
}
