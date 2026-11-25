package com.flightlog.lookup;

import com.flightlog.common.ApiException;
import com.flightlog.flight.FlightNumbers;
import com.flightlog.lookup.AeroDataBoxClient.AeroDataBoxException;
import com.flightlog.lookup.ApiCacheRepository.CachedResponse;
import com.flightlog.reference.AircraftTypeResolver;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 航班号自动查询：先查缓存，没有才调用 AeroDataBox。结果只用于预填表单，由用户确认后才保存。 */
@Service
public class FlightLookupService {

  /**
   * 预填表单用的一条航班。aircraftType 是换算出的 ICAO 机型代码，换算不了时为空（aircraftModel 保留原文）； aircraftSearch
   * 是只给了系列（Boeing 777）时用来搜索具体型号的词。
   */
  public record LookupCandidate(
      String flightNumber,
      LocalDate flightDate,
      String depIata,
      String arrIata,
      String depName,
      String arrName,
      String aircraftType,
      String aircraftModel,
      String aircraftSearch,
      String registration,
      String airlineName) {}

  public record LookupResponse(boolean cached, List<LookupCandidate> candidates) {}

  private static final String NOT_FOUND = "查不到这个航班，请检查航班号和日期，或手动填写";

  private final AeroDataBoxProperties properties;
  private final AeroDataBoxClient client;
  private final ApiCacheRepository cache;
  private final AircraftTypeResolver resolver;
  private final JsonMapper jsonMapper;

  public FlightLookupService(
      AeroDataBoxProperties properties,
      AeroDataBoxClient client,
      ApiCacheRepository cache,
      AircraftTypeResolver resolver,
      JsonMapper jsonMapper) {
    this.properties = properties;
    this.client = client;
    this.cache = cache;
    this.resolver = resolver;
    this.jsonMapper = jsonMapper;
  }

  public LookupResponse lookup(String rawNumber, String rawDate) {
    if (!properties.enabled()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "没有配置 AeroDataBox API key，航班号查询不可用，请手动填写");
    }
    String number = FlightNumbers.normalize(rawNumber);
    if (!FlightNumbers.isValid(number)) {
      throw ApiException.invalidField("number", "航班号格式不正确，例如 NH242");
    }
    LocalDate date;
    try {
      date = LocalDate.parse(rawDate == null ? "" : rawDate.strip());
    } catch (DateTimeParseException ex) {
      throw ApiException.invalidField("date", "日期格式应为 YYYY-MM-DD");
    }

    String key = "aerodatabox:" + number + ":" + date;
    Optional<CachedResponse> cached = cache.find(key);
    if (cached.isPresent()) {
      CachedResponse hit = cached.get();
      if (hit.responseJson() != null) {
        return new LookupResponse(true, parse(hit.responseJson(), date));
      }
      if (hit.fetchedAt().plus(properties.notFoundTtl()).isAfter(Instant.now())) {
        throw ApiException.notFound(NOT_FOUND);
      }
    }

    Optional<String> body;
    try {
      body = client.flightsByNumber(number, date);
    } catch (AeroDataBoxException ex) {
      throw switch (ex.failure()) {
        case QUOTA_EXCEEDED ->
            new ApiException(HttpStatus.TOO_MANY_REQUESTS, "今天的免费额度用完了，请手动填写或稍后再试");
        case INVALID_KEY ->
            new ApiException(HttpStatus.BAD_GATEWAY, "AeroDataBox API key 无效，请检查设置");
        case UNAVAILABLE -> new ApiException(HttpStatus.BAD_GATEWAY, "航班查询服务暂时不可用，请手动填写或稍后再试");
      };
    }

    List<LookupCandidate> candidates = body.map(json -> parse(json, date)).orElse(List.of());
    // 查到的结果永久缓存；查不到的缓存一段时间（只缓存成功的响应，错误不缓存）
    cache.put(key, candidates.isEmpty() ? null : body.get(), Instant.now());
    if (candidates.isEmpty()) {
      throw ApiException.notFound(NOT_FOUND);
    }
    return new LookupResponse(false, candidates);
  }

  private List<LookupCandidate> parse(String json, LocalDate requestedDate) {
    JsonNode root = jsonMapper.readTree(json);
    if (!root.isArray()) {
      return List.of();
    }
    List<LookupCandidate> result = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (JsonNode f : root) {
      String dep = text(f.path("departure").path("airport").path("iata"));
      String arr = text(f.path("arrival").path("airport").path("iata"));
      if (dep == null || arr == null) {
        continue;
      }
      LocalDate date = localDate(text(f.path("departure").path("scheduledTime").path("local")));
      if (date == null) {
        date = requestedDate;
      }
      if (!seen.add(dep + arr + date)) {
        continue; // 代码共享航班等重复的航段
      }
      String model = text(f.path("aircraft").path("model"));
      AircraftTypeResolver.Resolution type =
          model == null
              ? new AircraftTypeResolver.Resolution(null, null)
              : resolver.resolveOrFamily(model);
      String reg = text(f.path("aircraft").path("reg"));
      result.add(
          new LookupCandidate(
              FlightNumbers.normalize(text(f.path("number"))),
              date,
              dep.toUpperCase(Locale.ROOT),
              arr.toUpperCase(Locale.ROOT),
              text(f.path("departure").path("airport").path("name")),
              text(f.path("arrival").path("airport").path("name")),
              type.code(),
              model,
              type.search(),
              reg == null ? null : reg.replaceAll("\\s", "").toUpperCase(Locale.ROOT),
              text(f.path("airline").path("name"))));
    }
    return result;
  }

  private static String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asString();
    return value == null || value.isBlank() ? null : value.strip();
  }

  /** "2026-11-03 09:30+09:00" → 2026-11-03（出发地当地日期）。 */
  private static LocalDate localDate(String local) {
    if (local == null || local.length() < 10) {
      return null;
    }
    try {
      return LocalDate.parse(local.substring(0, 10));
    } catch (DateTimeParseException ex) {
      return null;
    }
  }
}
