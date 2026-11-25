package com.flightlog.ai;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 没有配置 Gemini key 时关掉 Spring AI 的聊天模型自动配置（spring.ai.model.chat=none）。
 *
 * <p>否则 Google GenAI 自动配置在启动时就会因为缺少 key 报错，整个应用起不来；规则要求缺 key 时应用照常启动， 只是 AI 功能不可用。
 *
 * <p>在配置文件和 .env 都加载之后执行，通过 META-INF/spring.factories 注册。
 */
public class AiAvailabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final String API_KEY = "spring.ai.google.genai.api-key";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    String key = environment.getProperty(API_KEY);
    if (key == null || key.isBlank()) {
      environment
          .getPropertySources()
          .addFirst(
              new MapPropertySource("flightlogAiDisabled", Map.of("spring.ai.model.chat", "none")));
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
