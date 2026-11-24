package com.flightlog.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class FlywayMigrationTest extends SqliteTestSupport {

  private final JdbcClient jdbc;

  FlywayMigrationTest(@Autowired JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Test
  void databaseFileIsCreatedInDataDir() {
    assertThat(Files.isRegularFile(dataDir.resolve("flightlog.db"))).isTrue();
  }

  @Test
  void flywayAppliedV1Successfully() {
    List<String> versions =
        jdbc.sql(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank")
            .query(String.class)
            .list();
    assertThat(versions).contains("1");
  }

  @Test
  void appMetaTableExists() {
    Integer count =
        jdbc.sql("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = :name")
            .param("name", "app_meta")
            .query(Integer.class)
            .single();
    assertThat(count).isEqualTo(1);
  }

  @Test
  void connectionUsesWalForeignKeysAndBusyTimeout() {
    assertThat(jdbc.sql("PRAGMA journal_mode").query(String.class).single())
        .isEqualToIgnoringCase("wal");
    assertThat(jdbc.sql("PRAGMA foreign_keys").query(Integer.class).single()).isEqualTo(1);
    assertThat(jdbc.sql("PRAGMA busy_timeout").query(Integer.class).single()).isEqualTo(5000);
  }
}
