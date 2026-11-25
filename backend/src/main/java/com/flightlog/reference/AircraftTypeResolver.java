package com.flightlog.reference;

import com.flightlog.reference.ReferenceDtos.AircraftTypeResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 把各种机型写法换算成机型表里的 ICAO 代码：
 *
 * <ul>
 *   <li>ICAO 代码本身（B789）、显示名（Boeing 787-9）、型号（787-9）
 *   <li>空客完整子型号：短横后第一位数字是系列（A320-212 → A320，A321-271NX → A21N，A350-1041 → A35K）
 *   <li>波音完整子型号：短横后第一位数字是系列，后面两位是客户代码（737-8AS → B738，777-3ZG(ER) → B77W）； 不带客户代码的 737-7/8/9/10 是
 *       737 MAX
 * </ul>
 *
 * 只返回机型表里存在的代码；只写了系列（Boeing 777）这类有多个型号的写法返回空，由 {@link #familySearch} 给出搜索词。
 */
@Component
public class AircraftTypeResolver {

  private static final Pattern AIRBUS =
      Pattern.compile("^A?(3\\d\\d)-(\\d+)(NX|NEO|N)?(?:\\(?[A-Z]+\\)?)?$");
  private static final Pattern BOEING =
      Pattern.compile("^(7\\d7)-(\\d{1,2})([0-9A-Z]{2})?(?:\\(?(ER|LR|F|SF|BCF)\\)?)?$");
  private static final Pattern MAX = Pattern.compile("^(?:737)?MAX(\\d{1,2})$");

  private static final Map<String, String> MAX_CODES =
      Map.of("7", "B37M", "8", "B38M", "9", "B39M", "10", "B3XM");

  private final ReferenceRepository repository;
  private volatile List<AircraftTypeResponse> cache;

  public AircraftTypeResolver(ReferenceRepository repository) {
    this.repository = repository;
  }

  public Optional<String> resolve(String text) {
    return resolve(text, types());
  }

  public Optional<String> familySearch(String text) {
    return familySearch(text, types());
  }

  /** 机型表只在首次启动时导入，之后不变，所以缓存在内存里。 */
  private List<AircraftTypeResponse> types() {
    List<AircraftTypeResponse> types = cache;
    if (types == null || types.isEmpty()) {
      types = repository.allAircraftTypes();
      cache = types;
    }
    return types;
  }

  static Optional<String> resolve(String text, List<AircraftTypeResponse> types) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    String wanted = normalize(text);
    String upper = text.strip().toUpperCase(Locale.ROOT);
    for (AircraftTypeResponse t : types) {
      if (t.icaoCode().equals(upper)
          || wanted.equals(normalize(t.displayName()))
          || wanted.equals(normalize(t.manufacturer() + t.model()))
          || wanted.equals(normalize(t.model()))) {
        return Optional.of(t.icaoCode());
      }
    }
    String compact = upper.replaceAll("^(AIRBUS|BOEING)\\s*", "").replaceAll("\\s", "");
    return subtypeCode(compact)
        .filter(code -> types.stream().anyMatch(t -> t.icaoCode().equals(code)));
  }

  /** 只写了系列、对应多个具体型号时（Boeing 777），返回用来搜索的系列名（777）。 */
  static Optional<String> familySearch(String text, List<AircraftTypeResponse> types) {
    if (text == null || text.isBlank() || resolve(text, types).isPresent()) {
      return Optional.empty();
    }
    String wanted = normalize(text);
    String withoutMaker = normalize(text.replaceAll("(?i)^\\s*(airbus|boeing)\\s*", ""));
    List<AircraftTypeResponse> matches =
        types.stream()
            .filter(t ->
                wanted.equals(normalize(t.manufacturer() + t.family()))
                    || withoutMaker.equals(normalize(t.family())))
            .toList();
    return matches.size() > 1 ? Optional.of(matches.getFirst().family()) : Optional.empty();
  }

  private static Optional<String> subtypeCode(String s) {
    Matcher max = MAX.matcher(s);
    if (max.matches()) {
      return Optional.ofNullable(MAX_CODES.get(max.group(1)));
    }
    Matcher airbus = AIRBUS.matcher(s);
    if (airbus.matches()) {
      return airbus(airbus.group(1), airbus.group(2), airbus.group(3) != null);
    }
    Matcher boeing = BOEING.matcher(s);
    if (boeing.matches()) {
      return boeing(boeing.group(1), boeing.group(2), boeing.group(3) != null, boeing.group(4));
    }
    return Optional.empty();
  }

  private static Optional<String> airbus(String base, String digits, boolean neo) {
    // A350-1041 的系列是 1000，其余看第一位数字
    String series = base.equals("350") && digits.startsWith("10") ? "10" : digits.substring(0, 1);
    String code =
        switch (base + "-" + series) {
          case "318-1" -> "A318";
          case "319-1" -> neo ? "A19N" : "A319";
          case "320-2" -> neo ? "A20N" : "A320";
          case "321-1", "321-2" -> neo ? "A21N" : "A321";
          case "300-6" -> "A306";
          case "310-2", "310-3" -> "A310";
          case "330-2" -> "A332";
          case "330-3" -> "A333";
          case "330-8" -> "A338";
          case "330-9" -> "A339";
          case "340-2" -> "A342";
          case "340-3" -> "A343";
          case "340-5" -> "A345";
          case "340-6" -> "A346";
          case "350-9" -> "A359";
          case "350-10" -> "A35K";
          case "380-8" -> "A388";
          default -> null;
        };
    return Optional.ofNullable(code);
  }

  private static Optional<String> boeing(
      String base, String series, boolean hasCustomerCode, String suffix) {
    String code =
        switch (base) {
          case "737" ->
              hasCustomerCode
                  ? (series.length() == 1 ? "B73" + series : null)
                  : MAX_CODES.get(series);
          case "747" -> Map.of("2", "B742", "4", "B744", "8", "B748").get(series);
          case "757" -> Map.of("2", "B752", "3", "B753").get(series);
          case "767" -> Map.of("2", "B762", "3", "B763", "4", "B764").get(series);
          case "777" ->
              switch (series) {
                case "2" -> "LR".equals(suffix) ? "B77L" : "B772";
                case "3" -> "ER".equals(suffix) ? "B77W" : "B773";
                case "8" -> hasCustomerCode ? null : "B778";
                case "9" -> hasCustomerCode ? null : "B779";
                default -> null;
              };
          case "787" -> Map.of("8", "B788", "9", "B789", "10", "B78X").get(series);
          case "717" -> "2".equals(series) ? "B712" : null;
          default -> null;
        };
    return Optional.ofNullable(code);
  }

  private static String normalize(String s) {
    return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }
}
