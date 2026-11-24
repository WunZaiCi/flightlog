package com.flightlog.flight;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import com.flightlog.auth.SessionClient;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class FlightFilterApiTest extends SqliteTestSupport {

  private final MockMvcTester mvc;
  private SessionClient user;

  FlightFilterApiTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  private SessionClient newUser() {
    SessionClient client = new SessionClient(mvc);
    client.register("f" + System.nanoTime() + "@example.com", "correct-horse-1", null);
    return client;
  }

  private void create(
      SessionClient owner,
      String date,
      String number,
      String dep,
      String arr,
      String type,
      String reg,
      String notes) {
    String body =
        """
        {"flightDate":"%s","flightNumber":"%s","depIata":"%s","arrIata":"%s",
         "aircraftType":"%s","registration":"%s","notes":"%s"}
        """
            .formatted(date, number, dep, arr, type, reg, notes);
    assertThat(owner.postJson("/api/flights", body)).hasStatus(HttpStatus.CREATED);
  }

  @BeforeEach
  void seed() {
    user = newUser();
    create(user, "2025-03-01", "NH242", "FUK", "HND", "B789", "JA891A", "第一次坐 787");
    create(user, "2025-09-10", "JL305", "HND", "FUK", "A359", "JA01XJ", "");
    create(user, "2026-02-14", "MU5101", "PVG", "PEK", "A333", "B-6129", "情人节");
    // 另一个用户的航班不能出现在任何筛选结果里
    create(newUser(), "2025-03-01", "NH242", "FUK", "HND", "B789", "JA891A", "别人的");
  }

  private List<String> numbers(String query) {
    var result = user.get("/api/flights" + query);
    assertThat(result).hasStatusOk();
    return JsonPath.read(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
        "$[*].flightNumber");
  }

  @Test
  void noFilterReturnsAllOwnFlights() {
    assertThat(numbers("")).containsExactly("MU5101", "JL305", "NH242");
  }

  @Test
  void filterByYear() {
    assertThat(numbers("?year=2025")).containsExactly("JL305", "NH242");
  }

  @Test
  void filterByAirlineIsCaseInsensitive() {
    assertThat(numbers("?airline=jl")).containsExactly("JL305");
  }

  @Test
  void filterByAircraftType() {
    assertThat(numbers("?type=A359")).containsExactly("JL305");
  }

  @Test
  void filterByAirportMatchesDepartureOrArrival() {
    assertThat(numbers("?airport=FUK")).containsExactly("JL305", "NH242");
  }

  @Test
  void filtersCombine() {
    assertThat(numbers("?year=2025&airport=FUK&type=B789")).containsExactly("NH242");
  }

  @Test
  void searchMatchesFlightNumberRegistrationCityNotesAndAircraftName() {
    assertThat(numbers("?q=nh2")).containsExactly("NH242");
    assertThat(numbers("?q=ja01")).containsExactly("JL305");
    assertThat(numbers("?q=beijing")).containsExactly("MU5101");
    assertThat(numbers("?q=情人节")).containsExactly("MU5101");
    assertThat(numbers("?q=787")).containsExactly("NH242");
  }

  @Test
  void searchTreatsWildcardsLiterally() {
    assertThat(numbers("?q=%25")).isEmpty();
    assertThat(numbers("?q=_")).isEmpty();
  }

  @Test
  void blankFiltersAreIgnored() {
    assertThat(numbers("?year=&airline=&type=&airport=&q=")).hasSize(3);
  }

  @Test
  void invalidYearIsRejected() {
    assertThat(user.get("/api/flights?year=abc")).hasStatus(HttpStatus.BAD_REQUEST);
  }
}
