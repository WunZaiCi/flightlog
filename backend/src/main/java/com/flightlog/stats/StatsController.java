package com.flightlog.stats;

import com.flightlog.auth.AppUserPrincipal;
import com.flightlog.flight.FlightFilter;
import com.flightlog.stats.StatsDtos.AircraftStatsResponse;
import com.flightlog.stats.StatsDtos.RoutesResponse;
import com.flightlog.stats.StatsDtos.SummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统计接口，只统计当前用户的航班。 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

  private final StatsService statsService;
  private final RouteStatsService routeStatsService;

  public StatsController(StatsService statsService, RouteStatsService routeStatsService) {
    this.statsService = statsService;
    this.routeStatsService = routeStatsService;
  }

  @GetMapping("/summary")
  public SummaryResponse summary(@AuthenticationPrincipal AppUserPrincipal user) {
    return statsService.summary(user.id());
  }

  @GetMapping("/aircraft")
  public AircraftStatsResponse aircraft(@AuthenticationPrincipal AppUserPrincipal user) {
    return statsService.aircraft(user.id());
  }

  @GetMapping("/routes")
  public RoutesResponse routes(
      @AuthenticationPrincipal AppUserPrincipal user,
      @RequestParam(required = false) String year,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String airline) {
    return routeStatsService.routes(user.id(), FlightFilter.of(year, airline, type, null, null));
  }
}
