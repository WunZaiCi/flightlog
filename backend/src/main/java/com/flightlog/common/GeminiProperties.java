package com.flightlog.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Gemini 配置（AI 解析，P7）。apiKey 为空时 AI 解析功能禁用。 */
@ConfigurationProperties("flightlog.gemini")
public record GeminiProperties(String apiKey) {

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }
}
