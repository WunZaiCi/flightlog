package com.flightlog.flight;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/** 航班接口的请求和响应。 */
public final class FlightDtos {

  private FlightDtos() {}

  static final String FLIGHT_NUMBER_REGEX =
      "^(?:[A-Z]{3}\\d{1,4}[A-Z]?|(?:[A-Z][A-Z0-9]|[0-9][A-Z])\\d{1,4}[A-Z]?)$";

  /**
   * 新增或修改航班。构造时先规范化：去掉首尾空格，空字符串当作没填；航班号、机场、机型、注册号转大写。
   *
   * @param allowDuplicate 用户确认「可能重复」后仍然保存
   */
  public record FlightRequest(
      @NotNull(message = "请填写日期") LocalDate flightDate,
      @NotBlank(message = "请填写航班号")
          @Pattern(regexp = FLIGHT_NUMBER_REGEX, message = "航班号格式不正确，例如 NH242")
          String flightNumber,
      @NotBlank(message = "请填写出发机场")
          @Pattern(regexp = "^[A-Z]{3}$", message = "出发机场请填写 IATA 三字码，例如 FUK")
          String depIata,
      @NotBlank(message = "请填写到达机场")
          @Pattern(regexp = "^[A-Z]{3}$", message = "到达机场请填写 IATA 三字码，例如 HND")
          String arrIata,
      @Pattern(regexp = "^[A-Z0-9]{2,4}$", message = "机型请填写 ICAO 机型代码，例如 B789") String aircraftType,
      @Pattern(regexp = "^[A-Z0-9-]{2,10}$", message = "注册号格式不正确，例如 JA891A") String registration,
      @Size(max = 500, message = "备注最多 500 个字") String notes,
      @Pattern(regexp = "^(manual|api|ai|csv)$", message = "来源不正确") String source,
      Boolean allowDuplicate) {

    public FlightRequest {
      flightNumber = FlightNumbers.normalize(blankToNull(flightNumber));
      depIata = upper(depIata);
      arrIata = upper(arrIata);
      aircraftType = upper(aircraftType);
      registration = registration == null ? null : upper(registration.replaceAll("\\s", ""));
      notes = blankToNull(notes);
      source = source == null || source.isBlank() ? "manual" : source.strip();
      // Jackson 3 默认不允许 null 赋给基本类型，所以用 Boolean，没传时当作 false
      allowDuplicate = Boolean.TRUE.equals(allowDuplicate);
    }

    private static String blankToNull(String s) {
      return s == null || s.isBlank() ? null : s.strip();
    }

    private static String upper(String s) {
      String v = blankToNull(s);
      return v == null ? null : v.toUpperCase(Locale.ROOT);
    }
  }

  public record AirportSummary(
      String iata, String name, String city, String countryCode, double lat, double lon) {}

  public record AircraftTypeSummary(
      String icaoCode,
      String manufacturer,
      String family,
      String model,
      String displayName,
      String body) {}

  public record FlightResponse(
      long id,
      LocalDate flightDate,
      String flightNumber,
      String airlineIata,
      String airlineName,
      AirportSummary dep,
      AirportSummary arr,
      String aircraftTypeIcao,
      boolean aircraftTypeRecognized,
      AircraftTypeSummary aircraftType,
      String registration,
      int distanceKm,
      String notes,
      String source,
      Instant createdAt,
      Instant updatedAt) {}
}
