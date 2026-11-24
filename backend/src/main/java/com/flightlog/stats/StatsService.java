package com.flightlog.stats;

import com.flightlog.flight.FlightRepository;
import com.flightlog.stats.StatsDtos.AircraftStatsResponse;
import com.flightlog.stats.StatsDtos.AircraftTypeStat;
import com.flightlog.stats.StatsDtos.AirlineCount;
import com.flightlog.stats.StatsDtos.BodyShare;
import com.flightlog.stats.StatsDtos.RegistrationCount;
import com.flightlog.stats.StatsDtos.SummaryResponse;
import com.flightlog.stats.StatsDtos.YearBodyCount;
import com.flightlog.stats.StatsRepository.Totals;
import com.flightlog.stats.StatsRepository.TypeAirline;
import com.flightlog.stats.StatsRepository.TypeRegistration;
import com.flightlog.stats.StatsRepository.YearBody;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  public AircraftStatsResponse aircraft(long userId) {
    LocalDate today = LocalDate.now();
    LocalDate newSince = today.minusDays(NEW_UNLOCK_DAYS);

    Map<String, List<AirlineCount>> airlines = new HashMap<>();
    for (TypeAirline a : stats.airlinesByType(userId)) {
      airlines
          .computeIfAbsent(a.icao(), k -> new ArrayList<>())
          .add(new AirlineCount(a.iata(), a.name(), a.count()));
    }
    Map<String, List<RegistrationCount>> registrations = new HashMap<>();
    for (TypeRegistration r : stats.registrationsByType(userId)) {
      registrations
          .computeIfAbsent(r.icao(), k -> new ArrayList<>())
          .add(new RegistrationCount(r.registration(), r.count()));
    }

    List<AircraftTypeStat> types =
        stats.typeAggregates(userId).stream()
            .map(t ->
                new AircraftTypeStat(
                    t.icao(),
                    t.manufacturer(),
                    t.family(),
                    t.model(),
                    t.displayName(),
                    t.body(),
                    t.displayName() != null,
                    t.flightCount(),
                    t.distanceKm(),
                    t.firstFlightDate(),
                    t.firstFlightNumber(),
                    !t.firstFlightDate().isBefore(newSince) && !t.firstFlightDate().isAfter(today),
                    airlines.getOrDefault(t.icao(), List.of()),
                    registrations.getOrDefault(t.icao(), List.of())))
            .toList();

    // 按年份汇总机身类型；总占比是各年之和
    Map<String, int[]> years = new LinkedHashMap<>();
    int[] total = new int[4];
    for (YearBody yb : stats.bodyByYear(userId)) {
      int slot = BODY_SLOTS.indexOf(yb.body());
      years.computeIfAbsent(yb.year(), k -> new int[4])[slot] += yb.count();
      total[slot] += yb.count();
    }
    List<YearBodyCount> byYear =
        years.entrySet().stream()
            .map(e ->
                new YearBodyCount(
                    e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
            .toList();

    return new AircraftStatsResponse(
        types, new BodyShare(total[0], total[1], total[2], total[3]), byYear);
  }

  private static final List<String> BODY_SLOTS = List.of("narrow", "wide", "regional", "unknown");
}
