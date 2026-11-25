package com.flightlog.ai;

import com.flightlog.ai.AiDtos.AiCandidate;
import com.flightlog.ai.AiDtos.ParseResponse;
import com.flightlog.ai.AiDtos.ParsedFlight;
import com.flightlog.ai.AiDtos.ParsedItinerary;
import com.flightlog.common.ApiException;
import com.flightlog.common.GeminiProperties;
import com.flightlog.flight.FlightNumbers;
import com.flightlog.reference.AircraftTypeResolver;
import com.flightlog.reference.ReferenceRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

/**
 * 用 Gemini 从文字或截图里提取航班（结构化输出）。结果只预填表单，用户逐条确认后才保存；图片只用于这一次解析，不保存。
 *
 * <p>免费额度有限：不自动重试，429 时提示用户手动填写。
 */
@Service
public class AiParseService {

  private static final Logger log = LoggerFactory.getLogger(AiParseService.class);

  private static final Pattern ICAO_CODE = Pattern.compile("^[A-Z0-9]{2,4}$");

  static final Set<String> IMAGE_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

  private static final String SYSTEM_PROMPT =
      """
      你是航班信息提取助手。从用户提供的文字或图片（登机牌、电子客票、行程确认邮件、一句话描述）中，
      提取用户乘坐的每一段航班。

      规则：
      - 每个航段一条；往返、中转拆成多条，按时间顺序排列。
      - flightNumber：航空公司代码加数字，去掉空格，例如 NH242。
      - date：出发地当地日期，格式 YYYY-MM-DD。今天是 {today}。
        只写了月和日时，用今天所在的年份；如果这样得到的日期比今天晚 60 天以上，就用前一年。
        看不出日期时填 null。
      - departureAirport、arrivalAirport：IATA 三字码。只有城市名或机场名时换成对应的三字码
        （例如 福冈→FUK、羽田→HND、成田→NRT、关西→KIX、上海浦东→PVG、北京首都→PEK）。
        同一城市有多个机场又无法判断时填 null。
      - aircraftType：ICAO 机型代码（例如 B789、A359、A21N）；内容里没有机型时填 null，不要猜。
      - registration：飞机注册号；没有就填 null。
      - notes：座位号、舱位等补充信息；没有就填 null。
      - 不要编造内容里没有的信息。内容里没有航班时返回空数组。
      """;

  private final GeminiProperties gemini;
  private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
  private final ReferenceRepository reference;
  private final AircraftTypeResolver resolver;

  public AiParseService(
      GeminiProperties gemini,
      ObjectProvider<ChatClient.Builder> chatClientBuilder,
      ReferenceRepository reference,
      AircraftTypeResolver resolver) {
    this.resolver = resolver;
    this.gemini = gemini;
    this.chatClientBuilder = chatClientBuilder;
    this.reference = reference;
  }

  public ParseResponse parseText(String text) {
    return parse(text.strip(), null, null);
  }

  public ParseResponse parseImage(byte[] bytes, String contentType) {
    if (contentType == null || !IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw ApiException.badRequest("只支持图片（JPG、PNG、WebP、HEIC）");
    }
    if (bytes.length == 0) {
      throw ApiException.badRequest("图片是空的，请重新选择");
    }
    return parse("请识别这张图片里的航班信息。", bytes, MimeType.valueOf(contentType));
  }

  private ParseResponse parse(String userText, byte[] image, MimeType imageType) {
    ChatClient.Builder builder = gemini.enabled() ? chatClientBuilder.getIfAvailable() : null;
    if (builder == null) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "没有配置 Gemini API key，AI 解析不可用，请手动填写");
    }

    ParsedItinerary itinerary;
    try {
      itinerary =
          builder
              .build()
              .prompt()
              .system(s -> s.text(SYSTEM_PROMPT).param("today", LocalDate.now().toString()))
              .user(u -> {
                u.text(userText);
                if (image != null) {
                  u.media(imageType, new ByteArrayResource(image));
                }
              })
              .call()
              .entity(ParsedItinerary.class);
    } catch (RuntimeException ex) {
      if (isQuotaExceeded(ex)) {
        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "今天的免费额度用完了，请手动填写或稍后再试");
      }
      log.warn("AI 解析失败：{}", ex.toString());
      throw new ApiException(HttpStatus.BAD_GATEWAY, "AI 解析暂时不可用，请手动填写或稍后再试");
    }

    List<AiCandidate> candidates = new ArrayList<>();
    if (itinerary != null && itinerary.flights() != null) {
      for (ParsedFlight f : itinerary.flights()) {
        if (f != null) {
          candidates.add(toCandidate(f));
        }
      }
    }
    if (candidates.isEmpty()) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "没有从内容里识别出航班，请手动填写");
    }
    return new ParseResponse(candidates);
  }

  private AiCandidate toCandidate(ParsedFlight f) {
    List<String> warnings = new ArrayList<>();

    String number = FlightNumbers.normalize(blankToNull(f.flightNumber()));
    if (number == null) {
      warnings.add("没有识别出航班号");
    } else if (!FlightNumbers.isValid(number)) {
      warnings.add("航班号 " + number + " 格式不对");
    }

    LocalDate date = null;
    if (blankToNull(f.date()) == null) {
      warnings.add("没有识别出日期");
    } else {
      try {
        date = LocalDate.parse(f.date().strip());
      } catch (DateTimeParseException ex) {
        warnings.add("日期 " + f.date() + " 格式不对");
      }
    }

    String dep = airport(f.departureAirport(), "出发机场", warnings);
    String arr = airport(f.arrivalAirport(), "到达机场", warnings);

    // 机型，按顺序：能换算成代码就换算（A320-212 → A320）；只有系列（777、B787）给出搜索词让用户选；
    // 剩下本身像 ICAO 代码的照常保留（未识别机型），其他写法提示手动选择
    String rawType = blankToNull(f.aircraftType());
    String type = null;
    String typeSearch = null;
    if (rawType != null) {
      AircraftTypeResolver.Resolution resolution = resolver.resolveOrFamily(rawType);
      if (resolution.code() != null) {
        type = resolution.code();
      } else if (resolution.search() != null) {
        typeSearch = resolution.search();
        warnings.add("机型「" + rawType + "」只有系列，请选择具体型号");
      } else if (ICAO_CODE.matcher(rawType.toUpperCase(Locale.ROOT)).matches()) {
        type = rawType.toUpperCase(Locale.ROOT);
      } else {
        warnings.add("机型「" + rawType + "」没能匹配到机型代码，请手动选择");
      }
    }

    String reg = blankToNull(f.registration());
    return new AiCandidate(
        number,
        date,
        dep,
        arr,
        type,
        typeSearch,
        reg == null ? null : reg.replaceAll("\\s", "").toUpperCase(Locale.ROOT),
        blankToNull(f.notes()),
        warnings);
  }

  private String airport(String raw, String label, List<String> warnings) {
    String code = upper(raw);
    if (code == null) {
      warnings.add("没有识别出" + label);
    } else if (reference.findAirport(code).isEmpty()) {
      warnings.add("机场 " + code + " 不在机场表里");
    }
    return code;
  }

  /** Gemini 免费额度用完时返回 429 / RESOURCE_EXHAUSTED（可能包在其他异常里）。 */
  static boolean isQuotaExceeded(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      String message = String.valueOf(t.getMessage());
      if (message.contains("429") || message.contains("RESOURCE_EXHAUSTED")) {
        return true;
      }
    }
    return false;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() || "null".equalsIgnoreCase(s.strip()) ? null : s.strip();
  }

  private static String upper(String s) {
    String v = blankToNull(s);
    return v == null ? null : v.toUpperCase(Locale.ROOT);
  }
}
