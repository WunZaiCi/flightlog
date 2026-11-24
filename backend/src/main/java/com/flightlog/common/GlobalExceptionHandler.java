package com.flightlog.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
    // body 传 null，让父类再从 messages.properties 解析中文 title（已设置的 detail 会保留）
    return handleExceptionInternal(ex, null, headers, status, request);
  }

  /** 表单校验失败：除了通用提示，还在 errors 里按字段给出第一条中文错误，前端显示在对应输入框下。 */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    ex.getBody().setProperty("errors", errors);
    return handleExceptionInternal(ex, null, headers, status, request);
  }

  /** 业务错误：状态码和中文信息由抛出方决定。 */
  @ExceptionHandler(ApiException.class)
  public ProblemDetail handleApiException(ApiException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    problem.setTitle(ProblemTitles.of(ex.status()));
    if (!ex.fieldErrors().isEmpty()) {
      problem.setProperty("errors", ex.fieldErrors());
    }
    ex.properties().forEach(problem::setProperty);
    return problem;
  }

  /** 控制器里抛出的认证异常（例如方法级鉴权），按 401 处理而不是当成 500。 */
  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(AuthenticationException ex) {
    return handleApiException(ApiException.unauthorized("请先登录"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return handleApiException(new ApiException(HttpStatus.FORBIDDEN, "没有权限执行此操作"));
  }

  /** 兜底：未预料的异常只记日志，不把内部信息返回给客户端。 */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("未处理的异常", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后再试");
    problem.setTitle("服务器错误");
    return problem;
  }
}
