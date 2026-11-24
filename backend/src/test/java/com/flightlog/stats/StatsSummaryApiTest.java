package com.flightlog.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import com.flightlog.auth.SessionClient;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class StatsSummaryApiTest extends SqliteTestSupport {

  private final MockMvcTester mvc;

  StatsSummaryApiTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  private SessionClient newUser() {
    SessionClient client = new SessionClient(mvc);
    client.register("s" + System.nanoTime() + "@example.com", "correct-horse-1", null);
    return client;
  }

  private static String daysAgo(int days) {
    return LocalDate.now().minusDays(days).toString();
  }

  private void create(
      SessionClient user, String date, String number, String dep, String arr, String type) {
    String body =
        """
        {"flightDate":"%s","flightNumber":"%s","depIata":"%s","arrIata":"%s","aircraftType":%s,
         "allowDuplicate":true}
        """
            .formatted(date, number, dep, arr, type == null ? "null" : "\"" + type + "\"");
    assertThat(user.postJson("/api/flights", body)).hasStatus(HttpStatus.CREATED);
  }

  @Test
  void emptySummaryForNewUser() {
    MvcTestResult result = newUser().get("/api/stats/summary");
    assertThat(result).hasStatusOk();
    var json = assertThat(result).bodyJson();
    json.extractingPath("$.flightCount").isEqualTo(0);
    json.extractingPath("$.aircraftTypeCount").isEqualTo(0);
    json.extractingPath("$.airlineCount").isEqualTo(0);
    json.extractingPath("$.airportCount").isEqualTo(0);
    json.extractingPath("$.totalDistanceKm").isEqualTo(0);
    json.extractingPath("$.recentFlights").isEqualTo(List.of());
    json.extractingPath("$.newlyUnlocked").isEqualTo(List.of());
  }

  @Test
  void countsDistanceRecentFlightsAndNewlyUnlockedTypes() {
    SessionClient user = newUser();
    // B789：200 天前第一次坐，10 天前又坐了一次 → 不算新解锁
    create(user, daysAgo(200), "NH241", "HND", "FUK", "B789");
    create(user, daysAgo(10), "NH242", "FUK", "HND", "B789");
    // A359：30 天前第一次坐 → 新解锁
    create(user, daysAgo(30), "JL305", "HND", "KIX", "A359");
    // 未识别机型 X9Z：5 天前 → 也算新解锁，但标记未识别
    create(user, daysAgo(5), "MU5101", "PVG", "PEK", "X9Z");
    // 没填机型
    create(user, daysAgo(400), "NH1", "HND", "FUK", null);
    // 将来的航班（已订票）：计入总数，但还不能算「解锁」
    create(user, LocalDate.now().plusDays(20).toString(), "NH3", "HND", "CTS", "B38M");
    // 另一个用户的航班不计入
    create(newUser(), daysAgo(1), "UA1", "SFO", "NRT", "B77W");

    MvcTestResult result = user.get("/api/stats/summary");
    var json = assertThat(result).hasStatusOk().bodyJson();
    json.extractingPath("$.flightCount").isEqualTo(6);
    json.extractingPath("$.aircraftTypeCount").isEqualTo(4);
    json.extractingPath("$.unrecognizedTypeCount").isEqualTo(1);
    json.extractingPath("$.airlineCount").isEqualTo(3);
    json.extractingPath("$.airportCount").isEqualTo(6);
    json.extractingPath("$.totalDistanceKm")
        .satisfies(km -> assertThat(((Number) km).intValue()).isBetween(4900, 5400));

    json.extractingPath("$.recentFlights.length()").isEqualTo(5);
    json.extractingPath("$.recentFlights[0].flightNumber").isEqualTo("NH3");

    json.extractingPath("$.newlyUnlocked[*].icaoCode").isEqualTo(List.of("X9Z", "A359"));
    json.extractingPath("$.newlyUnlocked[1].displayName").isEqualTo("Airbus A350-900");
    json.extractingPath("$.newlyUnlocked[1].firstFlightNumber").isEqualTo("JL305");
    json.extractingPath("$.newlyUnlocked[1].firstFlightDate").isEqualTo(daysAgo(30));
    json.extractingPath("$.newlyUnlocked[0].recognized").isEqualTo(false);
  }

  @Test
  void requiresLogin() {
    assertThat(new SessionClient(mvc).get("/api/stats/summary")).hasStatus(HttpStatus.UNAUTHORIZED);
  }
}
