package com.flightlog.stats;

import com.flightlog.flight.FlightDtos.FlightResponse;
import java.time.LocalDate;
import java.util.List;

/** 统计接口的响应。 */
public final class StatsDtos {

  private StatsDtos() {}

  /**
   * 首页总览。
   *
   * @param aircraftTypeCount 坐过的不同机型数（含未识别机型）
   * @param unrecognizedTypeCount 其中不在机型表里的数量
   * @param newlyUnlocked 首次乘坐在最近 90 天内的机型（最多 5 个）
   */
  public record SummaryResponse(
      int flightCount,
      int aircraftTypeCount,
      int unrecognizedTypeCount,
      int airlineCount,
      int airportCount,
      long totalDistanceKm,
      List<FlightResponse> recentFlights,
      List<UnlockedType> newlyUnlocked) {}

  /** 一个机型的首次乘坐。displayName 为空表示未识别机型。 */
  public record UnlockedType(
      String icaoCode,
      String displayName,
      boolean recognized,
      LocalDate firstFlightDate,
      String firstFlightNumber) {}

  /** 机型统计页。 */
  public record AircraftStatsResponse(
      List<AircraftTypeStat> types, BodyShare bodyShare, List<YearBodyCount> byYear) {}

  /**
   * 一个坐过的机型。未识别机型（不在机型表里）的 manufacturer、family、model、displayName、body 为空。
   *
   * @param isNew 首次乘坐在最近 90 天内（「新解锁」）
   */
  public record AircraftTypeStat(
      String icaoCode,
      String manufacturer,
      String family,
      String model,
      String displayName,
      String body,
      boolean recognized,
      int flightCount,
      long distanceKm,
      LocalDate firstFlightDate,
      String firstFlightNumber,
      boolean isNew,
      List<AirlineCount> airlines,
      List<RegistrationCount> registrations) {}

  public record AirlineCount(String iata, String name, int count) {}

  public record RegistrationCount(String registration, int count) {}

  /** 按航班次数统计的机身类型；unknown 是没填机型或未识别机型。 */
  public record BodyShare(int narrow, int wide, int regional, int unknown) {}

  public record YearBodyCount(String year, int narrow, int wide, int regional, int unknown) {}

  /** 航线地图。 */
  public record RoutesResponse(
      List<RouteStat> routes, List<AirportStat> airports, RouteTotals totals) {}

  /** 一条航线（往返合并，from/to 按字母排序），按次数倒序。flights 按日期倒序。 */
  public record RouteStat(
      String from, String to, int count, int distanceKm, List<RouteFlight> flights) {}

  public record RouteFlight(
      long id,
      LocalDate flightDate,
      String flightNumber,
      String depIata,
      String arrIata,
      String airlineName,
      String aircraft) {}

  /** 去过的机场；visits = 出发次数 + 到达次数。 */
  public record AirportStat(
      String iata,
      String name,
      String city,
      String countryCode,
      double lat,
      double lon,
      int visits,
      int departures,
      int arrivals) {}

  public record RouteSummary(String from, String to, int distanceKm) {}

  /** longest / shortest 在没有航班时为空。 */
  public record RouteTotals(
      int flightCount,
      long totalDistanceKm,
      int countryCount,
      RouteSummary longest,
      RouteSummary shortest) {}
}
