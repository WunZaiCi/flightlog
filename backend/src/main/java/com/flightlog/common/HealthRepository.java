package com.flightlog.common;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HealthRepository implements DatabasePing {

  private final JdbcClient jdbc;

  public HealthRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void ping() {
    jdbc.sql("SELECT 1").query(Integer.class).single();
  }
}
