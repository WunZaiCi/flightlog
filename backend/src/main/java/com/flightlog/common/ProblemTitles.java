package com.flightlog.common;

import org.springframework.http.HttpStatusCode;

/** ProblemDetail 的中文标题。 */
public final class ProblemTitles {

  private ProblemTitles() {}

  public static String of(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "请求有误";
      case 401 -> "未登录";
      case 403 -> "没有权限";
      case 404 -> "资源不存在";
      case 409 -> "数据冲突";
      case 413 -> "文件太大";
      case 429 -> "请求太频繁";
      case 502, 503, 504 -> "服务暂时不可用";
      default -> status.is5xxServerError() ? "服务器错误" : "请求失败";
    };
  }
}
