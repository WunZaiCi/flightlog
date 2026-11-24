package com.flightlog.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import com.flightlog.auth.SessionClient;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class AircraftStatsApiTest extends SqliteTestSupport {

  private final MockMvcTester mvc;

  AircraftStatsApiTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  private SessionClient newUser() {
    SessionClient client = new SessionClient(mvc);
    client.register("a" + System.nanoTime() + "@example.com", "correct-horse-1", null);
    return client;
  }

  private void create(
      SessionClient user,
      String date,
      String number,
      String dep,
      String arr,
      String type,
      String reg) {
    String body =
        """
        {"flightDate":"%s","flightNumber":"%s","depIata":"%s","arrIata":"%s","aircraftType":%s,
         "registration":%s,"allowDuplicate":true}
        """
            .formatted(date, number, dep, arr, quote(type), quote(reg));
    assertThat(user.postJson("/api/flights", body)).hasStatus(HttpStatus.CREATED);
  }

  private static String quote(String s) {
    return s == null ? "null" : "\"" + s + "\"";
  }

  private static Object read(MvcTestResult result, String path) {
    return JsonPath.read(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
  }

  @Test
  void aggregatesPerAircraftType() {
    SessionClient user = newUser();
    String recent = LocalDate.now().minusDays(5).toString();
    create(user, "2024-05-01", "NH241", "HND", "FUK", "B789", "JA891A");
    create(user, "2025-05-01", "NH242", "FUK", "HND", "B789", "JA891A");
    create(user, "2025-06-01", "JL305", "HND", "KIX", "B789", "JA861J");
    create(user, recent, "JL306", "KIX", "HND", "A359", null);
    create(user, "2025-07-01", "MU5101", "PVG", "PEK", "X9Z", null);
    create(user, "2025-08-01", "NH1", "HND", "FUK", null, null);
    create(newUser(), "2025-05-01", "UA1", "SFO", "NRT", "B789", "N12345");

    MvcTestResult result = user.get("/api/stats/aircraft");
    assertThat(result).hasStatusOk();

    // 按乘坐次数排序
    assertThat(read(result, "$.types[*].icaoCode")).isEqualTo(List.of("B789", "A359", "X9Z"));

    assertThat(read(result, "$.types[0].flightCount")).isEqualTo(3);
    assertThat(read(result, "$.types[0].manufacturer")).isEqualTo("Boeing");
    assertThat(read(result, "$.types[0].family")).isEqualTo("787");
    assertThat(read(result, "$.types[0].model")).isEqualTo("787-9");
    assertThat(read(result, "$.types[0].body")).isEqualTo("wide");
    assertThat(read(result, "$.types[0].firstFlightDate")).isEqualTo("2024-05-01");
    assertThat(read(result, "$.types[0].firstFlightNumber")).isEqualTo("NH241");
    assertThat(read(result, "$.types[0].isNew")).isEqualTo(false);
    assertThat(((Number) read(result, "$.types[0].distanceKm")).intValue()).isBetween(2150, 2250);
    assertThat(read(result, "$.types[0].airlines"))
        .isEqualTo(
            List.of(
                Map.of("iata", "NH", "name", "All Nippon Airways", "count", 2),
                Map.of("iata", "JL", "name", "Japan Airlines", "count", 1)));
    assertThat(read(result, "$.types[0].registrations"))
        .isEqualTo(
            List.of(
                Map.of("registration", "JA891A", "count", 2),
                Map.of("registration", "JA861J", "count", 1)));

    assertThat(read(result, "$.types[1].isNew")).isEqualTo(true);
    assertThat(read(result, "$.types[2].recognized")).isEqualTo(false);
    assertThat(read(result, "$.types[2].manufacturer")).isNull();
  }

  @Test
  void bodyShareAndYearlyTrendCountFlights() {
    SessionClient user = newUser();
    create(user, "2024-05-01", "NH241", "HND", "FUK", "B789", null);
    create(user, "2025-05-01", "NH242", "FUK", "HND", "B738", null);
    create(user, "2025-06-01", "JL305", "HND", "KIX", "E190", null);
    create(user, "2025-07-01", "MU5101", "PVG", "PEK", "X9Z", null);
    create(user, "2025-08-01", "NH1", "HND", "FUK", null, null);

    MvcTestResult result = user.get("/api/stats/aircraft");
    assertThat(read(result, "$.bodyShare"))
        .isEqualTo(Map.of("narrow", 1, "wide", 1, "regional", 1, "unknown", 2));
    assertThat(read(result, "$.byYear"))
        .isEqualTo(
            List.of(
                Map.of("year", "2024", "narrow", 0, "wide", 1, "regional", 0, "unknown", 0),
                Map.of("year", "2025", "narrow", 1, "wide", 0, "regional", 1, "unknown", 2)));
  }

  @Test
  void emptyForNewUser() {
    MvcTestResult result = newUser().get("/api/stats/aircraft");
    assertThat(read(result, "$.types")).isEqualTo(List.of());
    assertThat(read(result, "$.byYear")).isEqualTo(List.of());
    assertThat(read(result, "$.bodyShare"))
        .isEqualTo(Map.of("narrow", 0, "wide", 0, "regional", 0, "unknown", 0));
  }

  @Test
  void requiresLogin() {
    assertThat(new SessionClient(mvc).get("/api/stats/aircraft"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }
}
