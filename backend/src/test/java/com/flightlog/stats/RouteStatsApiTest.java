package com.flightlog.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import com.flightlog.auth.SessionClient;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
class RouteStatsApiTest extends SqliteTestSupport {

  private final MockMvcTester mvc;

  RouteStatsApiTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  private SessionClient newUser() {
    SessionClient client = new SessionClient(mvc);
    client.register("r" + System.nanoTime() + "@example.com", "correct-horse-1", null);
    return client;
  }

  private void create(
      SessionClient user, String date, String number, String dep, String arr, String type) {
    String body =
        """
        {"flightDate":"%s","flightNumber":"%s","depIata":"%s","arrIata":"%s","aircraftType":"%s",
         "allowDuplicate":true}
        """
            .formatted(date, number, dep, arr, type);
    assertThat(user.postJson("/api/flights", body)).hasStatus(HttpStatus.CREATED);
  }

  private static Object read(MvcTestResult result, String path) {
    return JsonPath.read(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
  }

  private SessionClient seeded() {
    SessionClient user = newUser();
    create(user, "2025-03-01", "NH241", "HND", "FUK", "B789");
    create(user, "2025-03-05", "NH242", "FUK", "HND", "B789");
    create(user, "2025-04-01", "NH21", "HND", "ITM", "B738");
    create(user, "2026-01-10", "SQ12", "SIN", "NRT", "A388");
    create(newUser(), "2025-03-01", "UA1", "SFO", "NRT", "B77W");
    return user;
  }

  @Test
  void mergesBothDirectionsIntoOneRoute() {
    MvcTestResult result = seeded().get("/api/stats/routes");
    assertThat(result).hasStatusOk();

    // 按次数倒序；往返合并成一条，端点按字母排序
    assertThat(read(result, "$.routes[0].from")).isEqualTo("FUK");
    assertThat(read(result, "$.routes[0].to")).isEqualTo("HND");
    assertThat(read(result, "$.routes[0].count")).isEqualTo(2);
    assertThat(read(result, "$.routes[0].flights[*].flightNumber"))
        .isEqualTo(List.of("NH242", "NH241"));
    assertThat(read(result, "$.routes.length()")).isEqualTo(3);
  }

  @Test
  void airportsCountEveryDepartureAndArrival() {
    MvcTestResult result = seeded().get("/api/stats/routes");
    assertThat(read(result, "$.airports[0].iata")).isEqualTo("HND");
    assertThat(read(result, "$.airports[0].visits")).isEqualTo(3);
    assertThat(read(result, "$.airports[0].departures")).isEqualTo(2);
    assertThat(read(result, "$.airports[0].arrivals")).isEqualTo(1);
    assertThat(read(result, "$.airports[*].iata"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .containsExactlyInAnyOrder("HND", "FUK", "ITM", "SIN", "NRT")
        .doesNotContain("SFO");
  }

  @Test
  void totalsIncludeCountriesLongestAndShortest() {
    MvcTestResult result = seeded().get("/api/stats/routes");
    assertThat(read(result, "$.totals.flightCount")).isEqualTo(4);
    assertThat(read(result, "$.totals.countryCount")).isEqualTo(2);
    assertThat(read(result, "$.totals.longest.from")).isEqualTo("NRT");
    assertThat(read(result, "$.totals.longest.to")).isEqualTo("SIN");
    assertThat(read(result, "$.totals.shortest.from")).isEqualTo("HND");
    assertThat(read(result, "$.totals.shortest.to")).isEqualTo("ITM");
    assertThat(((Number) read(result, "$.totals.totalDistanceKm")).intValue())
        .isBetween(7300, 7700);
  }

  @Test
  void filtersByYearTypeAndAirline() {
    SessionClient user = seeded();
    assertThat(read(user.get("/api/stats/routes?year=2026"), "$.totals.flightCount")).isEqualTo(1);
    assertThat(read(user.get("/api/stats/routes?type=B789"), "$.totals.flightCount")).isEqualTo(2);
    assertThat(read(user.get("/api/stats/routes?airline=sq"), "$.routes[0].from")).isEqualTo("NRT");
  }

  @Test
  void emptyWhenNoFlights() {
    MvcTestResult result = newUser().get("/api/stats/routes");
    assertThat(read(result, "$.routes")).isEqualTo(List.of());
    assertThat(read(result, "$.totals.flightCount")).isEqualTo(0);
    assertThat(read(result, "$.totals.longest")).isNull();
  }

  @Test
  void requiresLogin() {
    assertThat(new SessionClient(mvc).get("/api/stats/routes")).hasStatus(HttpStatus.UNAUTHORIZED);
  }
}
