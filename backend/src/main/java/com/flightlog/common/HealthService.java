package com.flightlog.common;

import org.springframework.stereotype.Service;

@Service
public class HealthService {

  public HealthResponse check() {
    return HealthResponse.ok();
  }
}
