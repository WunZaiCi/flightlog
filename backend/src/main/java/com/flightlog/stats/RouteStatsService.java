package com.flightlog.stats;

import com.flightlog.flight.FlightDtos.AirportSummary;
import com.flightlog.flight.FlightDtos.FlightResponse;
import com.flightlog.flight.FlightFilter;
import com.flightlog.flight.FlightRepository;
import com.flightlog.stats.StatsDtos.AirportStat;
import com.flightlog.stats.StatsDtos.RouteFlight;
import com.flightlog.stats.StatsDtos.RouteStat;
import com.flightlog.stats.StatsDtos.RouteSummary;
import com.flightlog.stats.StatsDtos.RouteTotals;
import com.flightlog.stats.StatsDtos.RoutesResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 航线地图的统计：复用航班列表查询（含筛选），在内存里按航线和机场汇总。 */
@Service
public class RouteStatsService {

  private final FlightRepository flights;

  public RouteStatsService(FlightRepository flights) {
    this.flights = flights;
  }

  public RoutesResponse routes(long userId, FlightFilter filter) {
    List<FlightResponse> list = flights.list(userId, filter); // 已按日期倒序

    Map<String, RouteAccumulator> routes = new LinkedHashMap<>();
    Map<String, AirportAccumulator> airports = new LinkedHashMap<>();
    long totalDistance = 0;

    for (FlightResponse f : list) {
      totalDistance += f.distanceKm();
      boolean depFirst = f.dep().iata().compareTo(f.arr().iata()) <= 0;
      String from = depFirst ? f.dep().iata() : f.arr().iata();
      String to = depFirst ? f.arr().iata() : f.dep().iata();
      routes
          .computeIfAbsent(from + "-" + to, k -> new RouteAccumulator(from, to, f.distanceKm()))
          .flights
          .add(
              new RouteFlight(
                  f.id(),
                  f.flightDate(),
                  f.flightNumber(),
                  f.dep().iata(),
                  f.arr().iata(),
                  f.airlineName(),
                  f.aircraftType() != null
                      ? f.aircraftType().displayName()
                      : f.aircraftTypeIcao()));
      airports.computeIfAbsent(f.dep().iata(), k -> new AirportAccumulator(f.dep())).departures++;
      airports.computeIfAbsent(f.arr().iata(), k -> new AirportAccumulator(f.arr())).arrivals++;
    }

    List<RouteStat> routeStats =
        routes.values().stream()
            .map(r -> new RouteStat(r.from, r.to, r.flights.size(), r.distanceKm, r.flights))
            .sorted(
                Comparator.comparingInt(RouteStat::count)
                    .reversed()
                    .thenComparing(RouteStat::from)
                    .thenComparing(RouteStat::to))
            .toList();

    List<AirportStat> airportStats =
        airports.values().stream()
            .map(AirportAccumulator::toStat)
            .sorted(
                Comparator.comparingInt(AirportStat::visits)
                    .reversed()
                    .thenComparing(AirportStat::iata))
            .toList();

    Set<String> countries =
        airportStats.stream()
            .map(AirportStat::countryCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Comparator<RouteStat> byDistance = Comparator.comparingInt(RouteStat::distanceKm);
    RouteSummary longest =
        routeStats.stream().max(byDistance).map(RouteStatsService::summary).orElse(null);
    RouteSummary shortest =
        routeStats.stream().min(byDistance).map(RouteStatsService::summary).orElse(null);

    return new RoutesResponse(
        routeStats,
        airportStats,
        new RouteTotals(list.size(), totalDistance, countries.size(), longest, shortest));
  }

  private static RouteSummary summary(RouteStat r) {
    return new RouteSummary(r.from(), r.to(), r.distanceKm());
  }

  private static final class RouteAccumulator {
    final String from;
    final String to;
    final int distanceKm;
    final List<RouteFlight> flights = new ArrayList<>();

    RouteAccumulator(String from, String to, int distanceKm) {
      this.from = from;
      this.to = to;
      this.distanceKm = distanceKm;
    }
  }

  private static final class AirportAccumulator {
    final AirportSummary airport;
    int departures;
    int arrivals;

    AirportAccumulator(AirportSummary airport) {
      this.airport = airport;
    }

    AirportStat toStat() {
      return new AirportStat(
          airport.iata(),
          airport.name(),
          airport.city(),
          airport.countryCode(),
          airport.lat(),
          airport.lon(),
          departures + arrivals,
          departures,
          arrivals);
    }
  }
}
