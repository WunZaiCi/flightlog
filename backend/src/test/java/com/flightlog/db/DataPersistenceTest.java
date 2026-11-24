package com.flightlog.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.FlightlogApplication;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 数据库重启后数据仍在：用同一个数据目录先后启动两次完整的应用。
 *
 * <p>第二次启动时 Flyway 只校验、不重新执行迁移，已有数据原样保留（绝不删库重建）。
 */
class DataPersistenceTest {

  @TempDir Path dataDir;

  @Test
  void dataSurvivesApplicationRestart() {
    String updatedAt = Instant.now().toString();
    long historyRowsAfterFirstStart;

    try (ConfigurableApplicationContext first = start()) {
      JdbcClient jdbc = first.getBean(JdbcClient.class);
      jdbc.sql("INSERT INTO app_meta (key, value, updated_at) VALUES (:key, :value, :updatedAt)")
          .param("key", "persistence-probe")
          .param("value", "还在吗")
          .param("updatedAt", updatedAt)
          .update();
      historyRowsAfterFirstStart = countHistoryRows(jdbc);
    }
    assertThat(dataDir.resolve("flightlog.db")).isRegularFile();

    try (ConfigurableApplicationContext second = start()) {
      JdbcClient jdbc = second.getBean(JdbcClient.class);

      Optional<String> value =
          jdbc.sql("SELECT value FROM app_meta WHERE key = :key")
              .param("key", "persistence-probe")
              .query(String.class)
              .optional();
      assertThat(value).contains("还在吗");

      List<String> versionsAppliedMoreThanOnce =
          jdbc.sql(
                  "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL"
                      + " GROUP BY version HAVING COUNT(*) > 1")
              .query(String.class)
              .list();
      assertThat(versionsAppliedMoreThanOnce).as("每个迁移只执行过一次，没有重建").isEmpty();
      assertThat(countHistoryRows(jdbc)).as("第二次启动没有执行任何迁移").isEqualTo(historyRowsAfterFirstStart);
    }
  }

  private static long countHistoryRows(JdbcClient jdbc) {
    return jdbc.sql("SELECT COUNT(*) FROM flyway_schema_history").query(Long.class).single();
  }

  private ConfigurableApplicationContext start() {
    // 用命令行参数指定数据目录：优先级最高，环境变量 FLIGHTLOG_DATA_DIR 也盖不过它
    return new SpringApplicationBuilder(FlightlogApplication.class)
        .run(
            "--flightlog.data-dir=" + dataDir.toAbsolutePath(),
            "--server.port=0",
            "--spring.main.banner-mode=off");
  }
}
