package com.flightlog.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 在数据源初始化之前确保 {@code flightlog.data-dir} 目录存在（SQLite 不会自动创建父目录）。
 *
 * <p>只创建目录，绝不删除或覆盖任何文件。
 */
@Component
public class DataDirectoryInitializer implements BeanPostProcessor, EnvironmentAware {

  static final String PROPERTY = "flightlog.data-dir";

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (bean instanceof DataSource) {
      createDataDirectory(environment.getRequiredProperty(PROPERTY));
    }
    return bean;
  }

  static void createDataDirectory(String dataDir) {
    try {
      Files.createDirectories(Path.of(dataDir));
    } catch (IOException ex) {
      throw new UncheckedIOException("无法创建数据目录：" + dataDir, ex);
    }
  }
}
