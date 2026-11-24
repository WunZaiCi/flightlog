package com.flightlog.common;

/** {@code GET /api/health} 的响应。 */
public record HealthResponse(String status) {

  public static HealthResponse ok() {
    return new HealthResponse("ok");
  }
}
