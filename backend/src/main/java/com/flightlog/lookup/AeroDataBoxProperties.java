package com.flightlog.lookup;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AeroDataBox（经 API.Market）配置。
 *
 * @param apiKey 为空时航班号查询功能禁用
 * @param keyHeader 放 key 的请求头（API.Market 是 x-magicapi-key）
 * @param notFoundTtl 「查不到」的结果缓存多久；查到的结果永久缓存
 */
@ConfigurationProperties("flightlog.aerodatabox")
public record AeroDataBoxProperties(
    String apiKey,
    String baseUrl,
    String keyHeader,
    Duration connectTimeout,
    Duration readTimeout,
    Duration notFoundTtl) {

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }
}
