package com.flightlog.stats;

import com.flightlog.auth.AppUserPrincipal;
import com.flightlog.stats.StatsDtos.AircraftStatsResponse;
import com.flightlog.stats.StatsDtos.SummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 统计接口，只统计当前用户的航班。 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

  private final StatsService statsService;

  public StatsController(StatsService statsService) {
    this.statsService = statsService;
  }

  @GetMapping("/summary")
  public SummaryResponse summary(@AuthenticationPrincipal AppUserPrincipal user) {
    return statsService.summary(user.id());
  }

  @GetMapping("/aircraft")
  public AircraftStatsResponse aircraft(@AuthenticationPrincipal AppUserPrincipal user) {
    return statsService.aircraft(user.id());
  }
}
