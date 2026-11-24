package com.flightlog.stats;

import com.flightlog.flight.FlightRepository;
import com.flightlog.stats.StatsDtos.SummaryResponse;
import com.flightlog.stats.StatsRepository.Totals;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

  /** 首次乘坐在这么多天之内的机型算「新解锁」（首页和机型收集墙共用）。 */
  static final int NEW_UNLOCK_DAYS = 90;

  private static final int RECENT_FLIGHTS = 5;
  private static final int MAX_UNLOCKED_ON_HOME = 5;

  private final StatsRepository stats;
  private final FlightRepository flights;

  public StatsService(StatsRepository stats, FlightRepository flights) {
    this.stats = stats;
    this.flights = flights;
  }

  public SummaryResponse summary(long userId) {
    Totals totals = stats.totals(userId);
    LocalDate today = LocalDate.now();
    return new SummaryResponse(
        totals.flightCount(),
        totals.aircraftTypeCount(),
        totals.unrecognizedTypeCount(),
        totals.airlineCount(),
        totals.airportCount(),
        totals.totalDistanceKm(),
        flights.recent(userId, RECENT_FLIGHTS),
        stats.unlockedBetween(
            userId, today.minusDays(NEW_UNLOCK_DAYS), today, MAX_UNLOCKED_ON_HOME));
  }
}
