package com.flightlog.common;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** {@code GET /api/health} 的响应。 */
public record HealthResponse(String status, String database) {

  public static HealthResponse ok() {
    return new HealthResponse("ok", "ok");
  }

  public static HealthResponse databaseDown() {
    return new HealthResponse("error", "error");
  }

  @JsonIgnore
  public boolean healthy() {
    return "ok".equals(status);
  }
}
