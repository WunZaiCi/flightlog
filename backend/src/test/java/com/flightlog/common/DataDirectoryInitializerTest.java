package com.flightlog.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataDirectoryInitializerTest {

  @TempDir Path tmp;

  @Test
  void createsMissingNestedDirectory() {
    Path dir = tmp.resolve("a/b/data");

    DataDirectoryInitializer.createDataDirectory(dir.toString());

    assertThat(dir).isDirectory();
  }

  @Test
  void keepsExistingFilesUntouched() throws Exception {
    Path db = Files.writeString(tmp.resolve("flightlog.db"), "existing");

    DataDirectoryInitializer.createDataDirectory(tmp.toString());

    assertThat(db).hasContent("existing");
  }
}
