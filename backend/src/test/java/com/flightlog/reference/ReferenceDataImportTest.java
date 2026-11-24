package com.flightlog.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.SqliteTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class ReferenceDataImportTest extends SqliteTestSupport {

  private final JdbcClient jdbc;
  private final ReferenceDataImporter importer;

  ReferenceDataImportTest(@Autowired JdbcClient jdbc, @Autowired ReferenceDataImporter importer) {
    this.jdbc = jdbc;
    this.importer = importer;
  }

  private long count(String table) {
    return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
  }

  @Test
  void importsAllAirportsWithIataCodes() {
    assertThat(count("airports")).isEqualTo(9053);

    Map<String, Object> fuk =
        jdbc.sql("SELECT * FROM airports WHERE iata = 'FUK'").query().singleRow();
    assertThat(fuk)
        .containsEntry("icao", "RJFF")
        .containsEntry("name", "Fukuoka Airport")
        .containsEntry("city", "Fukuoka")
        .containsEntry("country_code", "JP")
        .containsEntry("tz", "Asia/Tokyo")
        .containsEntry("kind", "large_airport");
    assertThat((Double) fuk.get("lat")).isBetween(33.5, 33.6);
    assertThat((Double) fuk.get("lon")).isBetween(130.4, 130.5);
  }

  @Test
  void importsActiveAirlinesWithCountryCode() {
    assertThat(count("airlines")).isBetween(500L, 2000L);
    assertThat(
            jdbc.sql("SELECT name, icao, country_code FROM airlines WHERE iata = 'NH'")
                .query()
                .listOfRows())
        .anySatisfy(row ->
            assertThat(row).containsEntry("icao", "ANA").containsEntry("country_code", "JP"));
  }

  @Test
  void importsAircraftTypes() {
    assertThat(count("aircraft_types")).isGreaterThan(80);
    assertThat(
            jdbc.sql("SELECT * FROM aircraft_types WHERE icao_code = 'B789'").query().singleRow())
        .containsEntry("manufacturer", "Boeing")
        .containsEntry("family", "787")
        .containsEntry("model", "787-9")
        .containsEntry("body", "wide");
  }

  @Test
  void runningAgainDoesNotOverwriteOrDuplicateExistingData() {
    long airportsBefore = count("airports");
    jdbc.sql("UPDATE airports SET name = '用户改过的名字' WHERE iata = 'HND'").update();

    importer.importIfEmpty();

    assertThat(count("airports")).isEqualTo(airportsBefore);
    assertThat(
            jdbc.sql("SELECT name FROM airports WHERE iata = 'HND'").query(String.class).single())
        .isEqualTo("用户改过的名字");
  }
}
