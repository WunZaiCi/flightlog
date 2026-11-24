package com.flightlog.reference;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 启动时导入参考数据（机场、航司、机型）。每张表为空时才导入，已有数据不覆盖。
 *
 * <p>数据文件和来源见 {@code resources/refdata/README.md}。
 */
@Component
public class ReferenceDataImporter implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ReferenceDataImporter.class);
  private static final String DIR = "refdata/";
  private static final Pattern IATA_AIRLINE = Pattern.compile("^[A-Z0-9]{2}$");
  private static final Pattern ICAO_AIRLINE = Pattern.compile("^[A-Z]{3}$");

  private final JdbcClient jdbc;
  private final TransactionTemplate transaction;
  private final JsonMapper jsonMapper;

  public ReferenceDataImporter(
      JdbcClient jdbc, TransactionTemplate transaction, JsonMapper jsonMapper) {
    this.jdbc = jdbc;
    this.transaction = transaction;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void run(ApplicationArguments args) {
    importIfEmpty();
  }

  public void importIfEmpty() {
    importTableIfEmpty("airports", "SELECT COUNT(*) FROM airports", this::importAirports);
    importTableIfEmpty("airlines", "SELECT COUNT(*) FROM airlines", this::importAirlines);
    importTableIfEmpty(
        "aircraft_types", "SELECT COUNT(*) FROM aircraft_types", this::importAircraftTypes);
  }

  /** countSql 是固定的整条 SQL（不拼接表名）。 */
  private void importTableIfEmpty(String name, String countSql, Consumer<JdbcClient> importer) {
    long existing = jdbc.sql(countSql).query(Long.class).single();
    if (existing > 0) {
      return;
    }
    transaction.executeWithoutResult(status -> importer.accept(jdbc));
    long imported = jdbc.sql(countSql).query(Long.class).single();
    log.info("已导入参考数据 {}：{} 条", name, imported);
  }

  private void importAirports(JdbcClient jdbc) {
    Map<String, String> timezones = new HashMap<>();
    for (List<String> row : readCsv("openflights-airports.dat")) {
      String iata = nullIfBlank(row.get(4));
      String tz = nullIfBlank(row.get(11));
      if (iata != null && tz != null) {
        timezones.put(iata, tz);
      }
    }

    List<List<String>> rows = readCsv("ourairports-airports.csv");
    Map<String, Integer> col = header(rows.getFirst());
    for (List<String> row : rows.subList(1, rows.size())) {
      String iata = row.get(col.get("iata_code"));
      jdbc.sql(
              """
              INSERT INTO airports (iata, icao, name, city, country_code, lat, lon, tz, kind)
              VALUES (:iata, :icao, :name, :city, :country, :lat, :lon, :tz, :kind)
              """)
          .param("iata", iata)
          .param("icao", nullIfBlank(row.get(col.get("icao_code"))))
          .param("name", row.get(col.get("name")))
          .param("city", nullIfBlank(row.get(col.get("municipality"))))
          .param("country", nullIfBlank(row.get(col.get("iso_country"))))
          .param("lat", Double.parseDouble(row.get(col.get("latitude_deg"))))
          .param("lon", Double.parseDouble(row.get(col.get("longitude_deg"))))
          .param("tz", timezones.get(iata))
          .param("kind", nullIfBlank(row.get(col.get("type"))))
          .update();
    }
  }

  private void importAirlines(JdbcClient jdbc) {
    Map<String, String> countryCodes = new HashMap<>();
    List<List<String>> countries = readCsv("ourairports-countries.csv");
    Map<String, Integer> col = header(countries.getFirst());
    for (List<String> row : countries.subList(1, countries.size())) {
      countryCodes.put(row.get(col.get("name")), row.get(col.get("code")));
    }

    // OpenFlights airlines.dat：id, name, alias, iata, icao, callsign, country, active
    for (List<String> row : readCsv("openflights-airlines.dat")) {
      if (row.size() < 8 || !"Y".equals(row.get(7))) {
        continue;
      }
      String iata = matchOrNull(row.get(3), IATA_AIRLINE);
      String icao = matchOrNull(row.get(4), ICAO_AIRLINE);
      if (iata == null && icao == null) {
        continue;
      }
      jdbc.sql(
              """
              INSERT INTO airlines (id, iata, icao, name, country_code)
              VALUES (:id, :iata, :icao, :name, :country)
              """)
          .param("id", Long.parseLong(row.get(0)))
          .param("iata", iata)
          .param("icao", icao)
          .param("name", row.get(1))
          .param("country", countryCodes.get(row.get(6)))
          .update();
    }
  }

  private void importAircraftTypes(JdbcClient jdbc) {
    List<AircraftTypeSeed> seeds;
    try (InputStream in = new ClassPathResource(DIR + "aircraft_types.json").getInputStream()) {
      seeds = jsonMapper.readValue(in, new TypeReference<>() {});
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    for (AircraftTypeSeed seed : seeds) {
      jdbc.sql(
              """
              INSERT INTO aircraft_types (icao_code, manufacturer, family, model, display_name, body)
              VALUES (:icao, :manufacturer, :family, :model, :displayName, :body)
              """)
          .param("icao", seed.icao())
          .param("manufacturer", seed.manufacturer())
          .param("family", seed.family())
          .param("model", seed.model())
          .param("displayName", seed.displayName())
          .param("body", seed.body())
          .update();
    }
  }

  private record AircraftTypeSeed(
      String icao,
      String manufacturer,
      String family,
      String model,
      String displayName,
      String body) {}

  private static List<List<String>> readCsv(String file) {
    try (InputStream in = new ClassPathResource(DIR + file).getInputStream()) {
      return CsvParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
    } catch (IOException ex) {
      throw new UncheckedIOException("无法读取参考数据 " + file, ex);
    }
  }

  private static Map<String, Integer> header(List<String> header) {
    Map<String, Integer> index = new HashMap<>();
    for (int i = 0; i < header.size(); i++) {
      index.put(header.get(i), i);
    }
    return index;
  }

  /** OpenFlights 用 \N 表示空值。 */
  private static String nullIfBlank(String value) {
    return value == null || value.isBlank() || "\\N".equals(value) ? null : value.strip();
  }

  private static String matchOrNull(String value, Pattern pattern) {
    String v = nullIfBlank(value);
    return v != null && pattern.matcher(v).matches() ? v : null;
  }
}
