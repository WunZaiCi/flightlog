package com.flightlog.flight;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 航班号规范化和解析。
 *
 * <p>格式：航司代码 + 1~4 位数字 + 可选的一个字母后缀。航司代码可以是 IATA 两位（字母或字母数字混合，至少一个字母， 如 NH、3U、9C），或 ICAO 三个字母（如
 * ANA）。
 */
public final class FlightNumbers {

  private static final Pattern ICAO_FORMAT = Pattern.compile("^([A-Z]{3})(\\d{1,4})([A-Z]?)$");
  private static final Pattern IATA_FORMAT =
      Pattern.compile("^([A-Z][A-Z0-9]|[0-9][A-Z])(\\d{1,4})([A-Z]?)$");

  private FlightNumbers() {}

  /** 去掉空格和连字符，转大写：{@code nh 242 → NH242}。 */
  public static String normalize(String raw) {
    return raw == null ? null : raw.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
  }

  public static boolean isValid(String normalized) {
    return normalized != null
        && (ICAO_FORMAT.matcher(normalized).matches() || IATA_FORMAT.matcher(normalized).matches());
  }

  /** IATA 两位航司代码（航班号以 ICAO 三字母代码开头时为空）。 */
  public static Optional<String> iataDesignator(String normalized) {
    if (normalized == null || ICAO_FORMAT.matcher(normalized).matches()) {
      return Optional.empty();
    }
    Matcher m = IATA_FORMAT.matcher(normalized);
    return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
  }

  public static Optional<String> icaoDesignator(String normalized) {
    if (normalized == null) {
      return Optional.empty();
    }
    Matcher m = ICAO_FORMAT.matcher(normalized);
    return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
  }
}
