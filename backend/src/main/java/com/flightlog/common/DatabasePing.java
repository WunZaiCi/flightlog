package com.flightlog.common;

/** 检查数据库能否响应；失败时抛出 {@link org.springframework.dao.DataAccessException}。 */
@FunctionalInterface
public interface DatabasePing {

  void ping();
}
