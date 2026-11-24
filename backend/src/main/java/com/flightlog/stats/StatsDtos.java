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
}
