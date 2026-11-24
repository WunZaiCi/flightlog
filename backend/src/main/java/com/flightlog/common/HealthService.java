package com.flightlog.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

  private static final Logger log = LoggerFactory.getLogger(HealthService.class);

  private final DatabasePing database;

  public HealthService(DatabasePing database) {
    this.database = database;
  }

  public HealthResponse check() {
    try {
      database.ping();
      return HealthResponse.ok();
    } catch (DataAccessException ex) {
      log.warn("健康检查：数据库无法访问", ex);
      return HealthResponse.databaseDown();
    }
  }
}
