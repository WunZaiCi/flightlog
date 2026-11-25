package com.flightlog.lookup;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import com.flightlog.auth.SessionClient;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 航班号查询：AeroDataBox 全部用 WireMock 模拟，测试中绝不调用真实 API。 */
@SpringBootTest
@AutoConfigureMockMvc
class FlightLookupApiTest extends SqliteTestSupport {

  @RegisterExtension
  static WireMockExtension api =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void aeroDataBox(DynamicPropertyRegistry registry) {
    registry.add("flightlog.aerodatabox.base-url", () -> api.baseUrl() + "/aerodatabox");
    registry.add("flightlog.aerodatabox.api-key", () -> "test-key");
    registry.add("flightlog.aerodatabox.read-timeout", () -> "2s");
  }

  private static final String NH242 =
      """
      [{
        "number": "NH 242",
        "status": "Expected",
        "codeshareStatus": "IsOperator",
        "departure": {
          "airport": {"icao": "RJFF", "iata": "FUK", "name": "Fukuoka", "municipalityName": "Fukuoka"},
          "scheduledTime": {"utc": "2026-11-03 00:30Z", "local": "2026-11-03 09:30+09:00"}
        },
        "arrival": {
          "airport": {"icao": "RJTT", "iata": "HND", "name": "Tokyo Haneda", "municipalityName": "Tokyo"},
          "scheduledTime": {"utc": "2026-11-03 02:10Z", "local": "2026-11-03 11:10+09:00"}
        },
        "aircraft": {"reg": "JA891A", "modeS": "86E3B1", "model": "Boeing 787-9"},
        "airline": {"name": "ANA", "iata": "NH", "icao": "ANA"}
      }]
      """;

  private final MockMvcTester mvc;

  FlightLookupApiTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  private SessionClient user() {
    SessionClient client = new SessionClient(mvc);
    client.register("l" + System.nanoTime() + "@example.com", "correct-horse-1", null);
    return client;
  }

  private static void stub(String number, String date, int status, String body) {
    api.stubFor(
        get(urlPathEqualTo("/aerodatabox/flights/number/" + number + "/" + date))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  private static int calls(String number, String date) {
    return api.countRequestsMatching(
            getRequestedFor(urlPathEqualTo("/aerodatabox/flights/number/" + number + "/" + date))
                .build())
        .getCount();
  }

  @Test
  void foundFlightPrefillsFields() {
    stub("NH242", "2026-11-03", 200, NH242);

    MvcTestResult result = user().get("/api/lookup/flight?number=nh 242&date=2026-11-03");

    assertThat(result).hasStatusOk();
    var json = assertThat(result).bodyJson();
    json.extractingPath("$.cached").isEqualTo(false);
    json.extractingPath("$.candidates[0].flightNumber").isEqualTo("NH242");
    json.extractingPath("$.candidates[0].flightDate").isEqualTo("2026-11-03");
    json.extractingPath("$.candidates[0].depIata").isEqualTo("FUK");
    json.extractingPath("$.candidates[0].arrIata").isEqualTo("HND");
    json.extractingPath("$.candidates[0].aircraftType").isEqualTo("B789");
    json.extractingPath("$.candidates[0].aircraftModel").isEqualTo("Boeing 787-9");
    json.extractingPath("$.candidates[0].registration").isEqualTo("JA891A");
    json.extractingPath("$.candidates[0].airlineName").isEqualTo("ANA");

    api.verify(
        getRequestedFor(urlPathEqualTo("/aerodatabox/flights/number/NH242/2026-11-03"))
            .withHeader("x-magicapi-key", equalTo("test-key")));
  }

  @Test
  void sameFlightAndDateIsServedFromCache() {
    stub("JL305", "2026-11-04", 200, NH242.replace("NH 242", "JL 305"));
    SessionClient user = user();

    assertThat(user.get("/api/lookup/flight?number=JL305&date=2026-11-04")).hasStatusOk();
    MvcTestResult second = user().get("/api/lookup/flight?number=jl 305&date=2026-11-04");

    assertThat(second).hasStatusOk().bodyJson().extractingPath("$.cached").isEqualTo(true);
    assertThat(calls("JL305", "2026-11-04")).isEqualTo(1);
  }

  @Test
  void unknownAircraftModelKeepsTextButNoCode() {
    stub("MU5101", "2026-11-05", 200, NH242.replace("Boeing 787-9", "Comac C939"));
    MvcTestResult result = user().get("/api/lookup/flight?number=MU5101&date=2026-11-05");
    assertThat(result).bodyJson().extractingPath("$.candidates[0].aircraftType").isNull();
    assertThat(result)
        .bodyJson()
        .extractingPath("$.candidates[0].aircraftModel")
        .isEqualTo("Comac C939");
  }

  @Test
  void notFoundIsFriendlyAndCachedForAWhile() {
    stub("NH999", "2026-11-06", 204, "");
    SessionClient user = user();

    MvcTestResult first = user.get("/api/lookup/flight?number=NH999&date=2026-11-06");
    assertThat(first).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(first).bodyJson().extractingPath("$.detail").isEqualTo("查不到这个航班，请检查航班号和日期，或手动填写");

    assertThat(user.get("/api/lookup/flight?number=NH999&date=2026-11-06"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(calls("NH999", "2026-11-06")).isEqualTo(1);
  }

  @Test
  void quotaExhaustedIsFriendlyAndNotCached() {
    stub("NH100", "2026-11-07", 429, "{\"message\":\"Too many requests\"}");
    SessionClient user = user();

    MvcTestResult result = user.get("/api/lookup/flight?number=NH100&date=2026-11-07");
    assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("今天的免费额度用完了，请手动填写或稍后再试");

    user.get("/api/lookup/flight?number=NH100&date=2026-11-07");
    assertThat(calls("NH100", "2026-11-07")).isEqualTo(2);
  }

  @Test
  void upstreamFailureIsBadGateway() {
    stub("NH101", "2026-11-08", 500, "{}");
    MvcTestResult result = user().get("/api/lookup/flight?number=NH101&date=2026-11-08");
    assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("航班查询服务暂时不可用，请手动填写或稍后再试");
  }

  @Test
  void slowUpstreamTimesOut() {
    api.stubFor(
        get(urlPathEqualTo("/aerodatabox/flights/number/NH102/2026-11-09"))
            .willReturn(aResponse().withStatus(200).withBody(NH242).withFixedDelay(4000)));
    assertThat(user().get("/api/lookup/flight?number=NH102&date=2026-11-09"))
        .hasStatus(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void invalidKeyIsReported() {
    stub("NH103", "2026-11-10", 401, "{}");
    MvcTestResult result = user().get("/api/lookup/flight?number=NH103&date=2026-11-10");
    assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("AeroDataBox API key 无效，请检查设置");
  }

  @Test
  void invalidInputIsRejectedWithoutCallingApi() {
    SessionClient user = user();
    assertThat(user.get("/api/lookup/flight?number=12&date=2026-11-03"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(user.get("/api/lookup/flight?number=NH242&date=yesterday"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(api.getAllServeEvents()).noneMatch(e -> e.getRequest().getUrl().contains("/12/"));
  }

  @Test
  void requiresLogin() {
    assertThat(new SessionClient(mvc).get("/api/lookup/flight?number=NH242&date=2026-11-03"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void configReportsLookupEnabled() {
    assertThat(new SessionClient(mvc).get("/api/config"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.lookupEnabled")
        .isEqualTo(true);
  }
}
