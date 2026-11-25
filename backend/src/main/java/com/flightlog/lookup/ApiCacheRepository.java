package com.flightlog.lookup;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 外部 API 结果缓存（api_cache 表）。 */
@Repository
public class ApiCacheRepository {

  /** responseJson 为空表示「查不到」。 */
  public record CachedResponse(String responseJson, Instant fetchedAt) {}

  private final JdbcClient jdbc;

  public ApiCacheRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<CachedResponse> find(String key) {
    return jdbc.sql("SELECT response_json, fetched_at FROM api_cache WHERE key = :key")
        .param("key", key)
        .query((rs, rowNum) ->
            new CachedResponse(
                rs.getString("response_json"), Instant.parse(rs.getString("fetched_at"))))
        .optional();
  }

  public void put(String key, String responseJson, Instant now) {
    jdbc.sql(
            """
            INSERT INTO api_cache (key, response_json, fetched_at) VALUES (:key, :json, :now)
            ON CONFLICT (key) DO UPDATE SET response_json = excluded.response_json,
                                            fetched_at = excluded.fetched_at
            """)
        .param("key", key)
        .param("json", responseJson)
        .param("now", now.toString())
        .update();
  }
}
