package com.flightlog.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.flightlog.SqliteTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class ReferenceApiTest extends SqliteTestSupport {

  private final MockMvcTester mvc;

  ReferenceApiTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  private MvcTestResult search(String path, String q) {
    return mvc.get().uri(path).param("q", q).with(user("tester")).exchange();
  }

  @Test
  void requiresLogin() {
    assertThat(mvc.get().uri("/api/ref/airports").param("q", "FUK"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void airportExactIataCodeComesFirst() {
    MvcTestResult result = search("/api/ref/airports", "fuk");
    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$[0].iata").isEqualTo("FUK");
    assertThat(result).bodyJson().extractingPath("$[0].city").isEqualTo("Fukuoka");
    assertThat(result).bodyJson().extractingPath("$[0].countryCode").isEqualTo("JP");
  }

  @Test
  void airportSearchByCityPrefersLargeAirports() {
    MvcTestResult result = search("/api/ref/airports", "Tokyo");
    assertThat(result)
        .bodyJson()
        .extractingPath("$[0].iata")
        .satisfies(iata -> assertThat(iata).isIn("HND", "NRT"));
  }

  @Test
  void airportSearchByIcaoCode() {
    assertThat(search("/api/ref/airports", "RJTT"))
        .bodyJson()
        .extractingPath("$[0].iata")
        .isEqualTo("HND");
  }

  @Test
  void aircraftTypeSearchByModelName() {
    MvcTestResult result = search("/api/ref/aircraft-types", "787-9");
    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$[0].icaoCode").isEqualTo("B789");
    assertThat(result).bodyJson().extractingPath("$[0].displayName").isEqualTo("Boeing 787-9");
  }

  @Test
  void aircraftTypeSearchByIcaoCode() {
    assertThat(search("/api/ref/aircraft-types", "a359"))
        .bodyJson()
        .extractingPath("$[0].icaoCode")
        .isEqualTo("A359");
  }

  @Test
  void airlineSearchByIataCode() {
    MvcTestResult result = search("/api/ref/airlines", "nh");
    assertThat(result).bodyJson().extractingPath("$[0].iata").isEqualTo("NH");
    assertThat(result).bodyJson().extractingPath("$[0].icao").isEqualTo("ANA");
  }

  @Test
  void blankQueryReturnsEmptyList() {
    assertThat(search("/api/ref/airports", " ")).bodyJson().isEqualTo("[]");
  }

  @Test
  void resultsAreLimited() {
    assertThat(search("/api/ref/airports", "a"))
        .bodyJson()
        .extractingPath("$.length()")
        .satisfies(n -> assertThat((Integer) n).isLessThanOrEqualTo(10));
  }

  @Test
  void aircraftTypeSearchUnderstandsFullSubtypeNames() {
    assertThat(search("/api/ref/aircraft-types", "A320-212"))
        .bodyJson()
        .extractingPath("$[0].icaoCode")
        .isEqualTo("A320");
    assertThat(search("/api/ref/aircraft-types", "777-3ZG(ER)"))
        .bodyJson()
        .extractingPath("$[0].icaoCode")
        .isEqualTo("B77W");
  }
}
