package com.flightlog.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class HealthServiceTest {

  @Test
  void reportsOkWhenDatabaseResponds() {
    HealthService service = new HealthService(() -> {});

    assertThat(service.check()).isEqualTo(new HealthResponse("ok", "ok"));
  }

  @Test
  void reportsErrorWhenDatabaseFails() {
    HealthService service =
        new HealthService(() -> {
          throw new DataAccessResourceFailureException("disk gone");
        });

    HealthResponse response = service.check();
    assertThat(response).isEqualTo(new HealthResponse("error", "error"));
    assertThat(response.healthy()).isFalse();
  }
}
