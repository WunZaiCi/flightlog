package com.flightlog;

import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 所有 {@code @SpringBootTest} 的基类：每个测试类用临时目录里的 SQLite 文件，跑真实的 Flyway 迁移。
 *
 * <p>{@code @DirtiesContext}：测试类结束时关闭 Spring 上下文和连接池，释放数据库文件，临时目录才能删除（Windows 上打开的文件不能删）。
 */
@DirtiesContext
public abstract class SqliteTestSupport {

  @TempDir protected static Path dataDir;

  @DynamicPropertySource
  static void sqliteDataDir(DynamicPropertyRegistry registry) {
    registry.add("flightlog.data-dir", () -> dataDir.toAbsolutePath().toString());
  }
}
