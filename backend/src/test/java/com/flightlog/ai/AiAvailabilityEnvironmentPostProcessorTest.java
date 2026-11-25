package com.flightlog.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class AiAvailabilityEnvironmentPostProcessorTest {

  private final AiAvailabilityEnvironmentPostProcessor processor =
      new AiAvailabilityEnvironmentPostProcessor();

  private StandardEnvironment environment(Map<String, Object> props) {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", props));
    return env;
  }

  @Test
  void disablesChatModelWhenKeyIsBlank() {
    StandardEnvironment env = environment(Map.of("spring.ai.google.genai.api-key", " "));
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("none");
  }

  @Test
  void disablesChatModelWhenKeyIsMissing() {
    StandardEnvironment env = environment(Map.of());
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("none");
  }

  @Test
  void leavesChatModelAloneWhenKeyIsPresent() {
    StandardEnvironment env = environment(Map.of("spring.ai.google.genai.api-key", "abc"));
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("spring.ai.model.chat")).isNull();
  }
}
