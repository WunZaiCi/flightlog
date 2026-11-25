package com.flightlog.common;

import com.flightlog.lookup.AeroDataBoxProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 前端据此启用或禁用依赖外部 API 的功能。只返回「有没有配置」，绝不返回 key 本身。 */
@RestController
public class AppConfigController {

  public record ConfigResponse(boolean aiEnabled, boolean lookupEnabled) {}

  private final GeminiProperties gemini;
  private final AeroDataBoxProperties aeroDataBox;

  public AppConfigController(GeminiProperties gemini, AeroDataBoxProperties aeroDataBox) {
    this.gemini = gemini;
    this.aeroDataBox = aeroDataBox;
  }

  @GetMapping("/api/config")
  public ConfigResponse config() {
    return new ConfigResponse(gemini.enabled(), aeroDataBox.enabled());
  }
}
