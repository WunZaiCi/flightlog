package com.flightlog.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** AI 解析的请求、模型结构化输出和响应。 */
public final class AiDtos {

  private AiDtos() {}

  public record ParseTextRequest(
      @NotBlank(message = "请粘贴要解析的文字") @Size(max = 5000, message = "文字太长，最多 5000 个字")
          String text) {}

  /** 模型的结构化输出（Spring AI 根据这个类型生成 JSON Schema 交给模型）。 */
  public record ParsedItinerary(
      @JsonPropertyDescription("按时间顺序的每一段航班") List<ParsedFlight> flights) {}

  public record ParsedFlight(
      @JsonPropertyDescription("航班号：航空公司代码加数字，例如 NH242") String flightNumber,
      @JsonPropertyDescription("出发地当地日期，格式 YYYY-MM-DD；不确定时为 null") String date,
      @JsonPropertyDescription("出发机场 IATA 三字码，例如 FUK") String departureAirport,
      @JsonPropertyDescription("到达机场 IATA 三字码，例如 HND") String arrivalAirport,
      @JsonPropertyDescription("ICAO 机型代码，例如 B789；不知道时为 null") String aircraftType,
      @JsonPropertyDescription("飞机注册号，例如 JA891A；没有时为 null") String registration,
      @JsonPropertyDescription("座位、舱位等补充信息；没有时为 null") String notes) {}

  /**
   * 预填表单用的一条航班，用户确认后才保存。
   *
   * @param warnings 需要用户特别核对的地方（例如没识别出日期、机场不在机场表里）
   */
  public record AiCandidate(
      String flightNumber,
      LocalDate flightDate,
      String depIata,
      String arrIata,
      String aircraftType,
      String registration,
      String notes,
      List<String> warnings) {}

  public record ParseResponse(List<AiCandidate> flights) {}
}
