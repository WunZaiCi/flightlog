package com.flightlog.lookup;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 调用 AeroDataBox「按航班号和日期查航班」。不重试：免费额度有限，失败就让用户手动填写。
 *
 * <p>只在服务层确认缓存里没有结果时才调用。
 */
@Component
public class AeroDataBoxClient {

  private static final Logger log = LoggerFactory.getLogger(AeroDataBoxClient.class);

  /** 外部服务调用失败的原因。 */
  public enum Failure {
    QUOTA_EXCEEDED,
    INVALID_KEY,
    UNAVAILABLE
  }

  public static class AeroDataBoxException extends RuntimeException {
    private final Failure failure;

    AeroDataBoxException(Failure failure, String message, Throwable cause) {
      super(message, cause);
      this.failure = failure;
    }

    public Failure failure() {
      return failure;
    }
  }

  private final AeroDataBoxProperties properties;
  private final RestClient restClient;

  public AeroDataBoxClient(AeroDataBoxProperties properties) {
    this.properties = properties;
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  /** 返回原始 JSON；查不到（204 或 404）时为空。 */
  public Optional<String> flightsByNumber(String flightNumber, LocalDate date) {
    try {
      return restClient
          .get()
          .uri(
              properties.baseUrl()
                  + "/flights/number/{number}/{date}?withAircraftImage=false&withLocation=false",
              flightNumber,
              date.toString())
          .header(properties.keyHeader(), properties.apiKey())
          .exchange((request, response) -> {
            int status = response.getStatusCode().value();
            if (status == 200) {
              String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
              return body.isBlank() ? Optional.<String>empty() : Optional.of(body);
            }
            if (status == 204 || status == 404) {
              return Optional.<String>empty();
            }
            if (status == 429) {
              throw new AeroDataBoxException(Failure.QUOTA_EXCEEDED, "额度用完", null);
            }
            if (status == 401 || status == 403) {
              throw new AeroDataBoxException(Failure.INVALID_KEY, "key 无效：" + status, null);
            }
            throw new AeroDataBoxException(Failure.UNAVAILABLE, "HTTP " + status, null);
          });
    } catch (ResourceAccessException ex) {
      // 超时、断网等
      log.warn("AeroDataBox 无法访问：{}", ex.getMessage());
      throw new AeroDataBoxException(Failure.UNAVAILABLE, "无法访问", ex);
    }
  }
}
