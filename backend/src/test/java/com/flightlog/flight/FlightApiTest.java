package com.flightlog.flight;

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
class FlightApiTest extends SqliteTestSupport {

  private final MockMvcTester mvc;

  FlightApiTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  private SessionClient newUser() {
    SessionClient client = new SessionClient(mvc);
    MvcTestResult result =
        client.register("u" + System.nanoTime() + "@example.com", "correct-horse-1", null);
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return client;
  }

  private static String flightJson(
      String date, String number, String dep, String arr, String type, String reg) {
    return """
    {"flightDate":"%s","flightNumber":"%s","depIata":"%s","arrIata":"%s",
     "aircraftType":%s,"registration":%s,"notes":"窗边座位"}
    """
        .formatted(date, number, dep, arr, quoteOrNull(type), quoteOrNull(reg));
  }

  private static String quoteOrNull(String s) {
    return s == null ? "null" : "\"" + s + "\"";
  }

  private static long id(MvcTestResult result) {
    return ((Number) JsonPath.read(body(result), "$.id")).longValue();
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  private long create(SessionClient user, String date, String number, String dep, String arr) {
    MvcTestResult result =
        user.postJson("/api/flights", flightJson(date, number, dep, arr, "B789", null));
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return id(result);
  }

  // ---- 新增 ----

  @Test
  void createNormalizesInputAndComputesDistance() {
    SessionClient user = newUser();

    MvcTestResult result =
        user.postJson(
            "/api/flights", flightJson("2026-11-03", "nh 242", "fuk", "hnd", "b789", "ja891a"));

    assertThat(result).hasStatus(HttpStatus.CREATED);
    var json = assertThat(result).bodyJson();
    json.extractingPath("$.flightNumber").isEqualTo("NH242");
    json.extractingPath("$.flightDate").isEqualTo("2026-11-03");
    json.extractingPath("$.airlineIata").isEqualTo("NH");
    json.extractingPath("$.airlineName").isEqualTo("All Nippon Airways");
    json.extractingPath("$.dep.iata").isEqualTo("FUK");
    json.extractingPath("$.dep.city").isEqualTo("Fukuoka");
    json.extractingPath("$.arr.iata").isEqualTo("HND");
    json.extractingPath("$.aircraftTypeIcao").isEqualTo("B789");
    json.extractingPath("$.aircraftTypeRecognized").isEqualTo(true);
    json.extractingPath("$.aircraftType.displayName").isEqualTo("Boeing 787-9");
    json.extractingPath("$.registration").isEqualTo("JA891A");
    json.extractingPath("$.notes").isEqualTo("窗边座位");
    json.extractingPath("$.source").isEqualTo("manual");
    json.extractingPath("$.distanceKm")
        .satisfies(km -> assertThat(((Number) km).intValue()).isBetween(870, 890));
  }

  @Test
  void unknownAircraftTypeIsSavedButMarkedUnrecognized() {
    SessionClient user = newUser();
    MvcTestResult result =
        user.postJson(
            "/api/flights", flightJson("2026-01-02", "MU5101", "PVG", "PEK", "x9z", null));

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result).bodyJson().extractingPath("$.aircraftTypeIcao").isEqualTo("X9Z");
    assertThat(result).bodyJson().extractingPath("$.aircraftTypeRecognized").isEqualTo(false);
    assertThat(result).bodyJson().extractingPath("$.aircraftType").isNull();
  }

  @Test
  void aircraftTypeIsOptional() {
    SessionClient user = newUser();
    MvcTestResult result =
        user.postJson("/api/flights", flightJson("2026-01-02", "MU5101", "PVG", "PEK", null, null));
    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(result).bodyJson().extractingPath("$.aircraftTypeIcao").isNull();
  }

  @Test
  void unknownAirportIsRejectedWithFieldError() {
    MvcTestResult result =
        newUser()
            .postJson("/api/flights", flightJson("2026-01-02", "NH1", "ZZZ", "HND", null, null));
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.errors.depIata").isEqualTo("找不到机场 ZZZ");
  }

  @Test
  void sameDepartureAndArrivalIsRejected() {
    MvcTestResult result =
        newUser()
            .postJson("/api/flights", flightJson("2026-01-02", "NH1", "HND", "HND", null, null));
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.errors.arrIata").isEqualTo("出发和到达机场不能相同");
  }

  @Test
  void invalidInputReturnsChineseFieldErrors() {
    MvcTestResult result =
        newUser()
            .postJson(
                "/api/flights",
                "{\"flightDate\":null,\"flightNumber\":\"12\",\"depIata\":\"FUK\"}");
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    var json = assertThat(result).bodyJson();
    json.extractingPath("$.errors.flightDate").isEqualTo("请填写日期");
    json.extractingPath("$.errors.flightNumber").isEqualTo("航班号格式不正确，例如 NH242");
    json.extractingPath("$.errors.arrIata").isEqualTo("请填写到达机场");
  }

  // ---- 重复提示 ----

  @Test
  void duplicateFlightNeedsConfirmation() {
    SessionClient user = newUser();
    long first = create(user, "2026-11-03", "NH242", "FUK", "HND");

    MvcTestResult duplicate =
        user.postJson("/api/flights", flightJson("2026-11-03", "nh242", "FUK", "HND", null, null));
    assertThat(duplicate).hasStatus(HttpStatus.CONFLICT);
    assertThat(duplicate).bodyJson().extractingPath("$.detail").isEqualTo("这一天已经录入过 NH242，可能是重复录入");
    assertThat(duplicate)
        .bodyJson()
        .extractingPath("$.duplicateIds")
        .isEqualTo(List.of((int) first));

    String confirmed =
        flightJson("2026-11-03", "NH242", "FUK", "HND", null, null)
            .replace("\"notes\"", "\"allowDuplicate\":true,\"notes\"");
    assertThat(user.postJson("/api/flights", confirmed)).hasStatus(HttpStatus.CREATED);
  }

  @Test
  void otherUsersFlightsDoNotCountAsDuplicates() {
    create(newUser(), "2026-11-03", "NH242", "FUK", "HND");
    create(newUser(), "2026-11-03", "NH242", "FUK", "HND");
  }

  // ---- 查看、修改、删除 ----

  @Test
  void getUpdateAndDeleteOwnFlight() {
    SessionClient user = newUser();
    long id = create(user, "2026-11-03", "NH242", "FUK", "HND");

    assertThat(user.get("/api/flights/" + id))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.flightNumber")
        .isEqualTo("NH242");

    MvcTestResult updated =
        user.putJson(
            "/api/flights/" + id, flightJson("2026-11-04", "NH241", "HND", "KIX", "A359", null));
    assertThat(updated).hasStatusOk();
    assertThat(updated).bodyJson().extractingPath("$.flightNumber").isEqualTo("NH241");
    assertThat(updated).bodyJson().extractingPath("$.arr.iata").isEqualTo("KIX");
    assertThat(updated)
        .bodyJson()
        .extractingPath("$.distanceKm")
        .satisfies(km -> assertThat(((Number) km).intValue()).isBetween(420, 445));

    assertThat(user.delete("/api/flights/" + id)).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(user.get("/api/flights/" + id)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void updateIsNotADuplicateOfItself() {
    SessionClient user = newUser();
    long id = create(user, "2026-11-03", "NH242", "FUK", "HND");
    assertThat(
            user.putJson(
                "/api/flights/" + id,
                flightJson("2026-11-03", "NH242", "FUK", "HND", null, "JA123A")))
        .hasStatusOk();
  }

  @Test
  void editingAConfirmedDuplicateWithoutChangingDateOrNumberDoesNotWarnAgain() {
    SessionClient user = newUser();
    create(user, "2026-11-03", "NH242", "FUK", "HND");
    String confirmed =
        flightJson("2026-11-03", "NH242", "FUK", "HND", null, null)
            .replace("\"notes\"", "\"allowDuplicate\":true,\"notes\"");
    long second = id(user.postJson("/api/flights", confirmed));

    // 只改机型：不再提示
    assertThat(
            user.putJson(
                "/api/flights/" + second,
                flightJson("2026-11-03", "NH242", "FUK", "HND", "A359", null)))
        .hasStatusOk();
    // 改成另一个已存在的日期和航班号组合：仍然提示
    create(user, "2026-11-05", "NH244", "FUK", "HND");
    assertThat(
            user.putJson(
                "/api/flights/" + second,
                flightJson("2026-11-05", "NH244", "FUK", "HND", null, null)))
        .hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void listIsSortedByDateDescending() {
    SessionClient user = newUser();
    create(user, "2025-05-01", "NH1", "HND", "FUK");
    create(user, "2026-01-01", "NH2", "FUK", "HND");
    create(user, "2025-12-31", "NH3", "HND", "KIX");

    assertThat(user.get("/api/flights"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[*].flightNumber")
        .isEqualTo(List.of("NH2", "NH3", "NH1"));
  }

  // ---- 越权访问一律 404（P2 要求）----

  @Test
  void otherUsersFlightIsNotFoundForEveryOperation() {
    SessionClient alice = newUser();
    SessionClient mallory = newUser();
    long aliceFlight = create(alice, "2026-11-03", "NH242", "FUK", "HND");

    for (MvcTestResult result :
        List.of(
            mallory.get("/api/flights/" + aliceFlight),
            mallory.putJson(
                "/api/flights/" + aliceFlight,
                flightJson("2026-01-01", "XX1", "HND", "KIX", null, null)),
            mallory.delete("/api/flights/" + aliceFlight))) {
      assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
      assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("航班不存在");
    }

    // Alice 的数据没有被改动或删除，Mallory 的列表里看不到
    assertThat(alice.get("/api/flights/" + aliceFlight))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.flightNumber")
        .isEqualTo("NH242");
    assertThat(mallory.get("/api/flights")).bodyJson().isEqualTo("[]");
  }

  @Test
  void nonexistentFlightIsNotFound() {
    assertThat(newUser().get("/api/flights/999999")).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void requiresLogin() {
    assertThat(new SessionClient(mvc).get("/api/flights")).hasStatus(HttpStatus.UNAUTHORIZED);
  }
}
