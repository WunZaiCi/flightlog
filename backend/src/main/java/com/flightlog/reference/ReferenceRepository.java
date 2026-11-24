package com.flightlog.reference;

import com.flightlog.reference.ReferenceDtos.AircraftTypeResponse;
import com.flightlog.reference.ReferenceDtos.AirlineResponse;
import com.flightlog.reference.ReferenceDtos.AirportResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReferenceRepository {

  private final JdbcClient jdbc;

  public ReferenceRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** 按 IATA、ICAO、城市、机场名搜索；完全匹配代码的排最前，大机场优先。 */
  public List<AirportResponse> searchAirports(String query, int limit) {
    Like like = Like.of(query);
    return jdbc.sql(
            """
            SELECT iata, icao, name, city, country_code, lat, lon, tz FROM airports
            WHERE iata = :exact OR icao = :exact OR iata LIKE :prefix ESCAPE '\\'
               OR city LIKE :contains ESCAPE '\\' OR name LIKE :contains ESCAPE '\\'
            ORDER BY
              CASE
                WHEN iata = :exact THEN 0
                WHEN icao = :exact THEN 1
                WHEN city LIKE :prefix ESCAPE '\\' THEN 2
                WHEN iata LIKE :prefix ESCAPE '\\' THEN 3
                WHEN name LIKE :prefix ESCAPE '\\' THEN 4
                ELSE 5
              END,
              CASE kind WHEN 'large_airport' THEN 0 WHEN 'medium_airport' THEN 1 ELSE 2 END,
              name
            LIMIT :limit
            """)
        .param("exact", like.exact())
        .param("prefix", like.prefix())
        .param("contains", like.contains())
        .param("limit", limit)
        .query(ReferenceRepository::airport)
        .list();
  }

  public Optional<AirportResponse> findAirport(String iata) {
    return jdbc.sql(
            "SELECT iata, icao, name, city, country_code, lat, lon, tz FROM airports WHERE iata = :iata")
        .param("iata", iata)
        .query(ReferenceRepository::airport)
        .optional();
  }

  /** 按 IATA、ICAO、名称搜索航司。 */
  public List<AirlineResponse> searchAirlines(String query, int limit) {
    Like like = Like.of(query);
    return jdbc.sql(
            """
            SELECT iata, icao, name, country_code FROM airlines
            WHERE iata = :exact OR icao = :exact OR name LIKE :contains ESCAPE '\\'
            ORDER BY
              CASE
                WHEN iata = :exact THEN 0
                WHEN icao = :exact THEN 1
                WHEN name LIKE :prefix ESCAPE '\\' THEN 2
                ELSE 3
              END,
              CASE WHEN iata IS NOT NULL AND icao IS NOT NULL THEN 0 ELSE 1 END,
              name
            LIMIT :limit
            """)
        .param("exact", like.exact())
        .param("prefix", like.prefix())
        .param("contains", like.contains())
        .param("limit", limit)
        .query(ReferenceRepository::airline)
        .list();
  }

  /** 航班号前缀（IATA 两位码）对应的航司，取同时有 IATA 和 ICAO 代码的那条。 */
  public Optional<AirlineResponse> findAirlineByIata(String iata) {
    return jdbc.sql(
            """
            SELECT iata, icao, name, country_code FROM airlines WHERE iata = :iata
            ORDER BY CASE WHEN icao IS NOT NULL THEN 0 ELSE 1 END, id
            LIMIT 1
            """)
        .param("iata", iata)
        .query(ReferenceRepository::airline)
        .optional();
  }

  /** 按 ICAO 机型代码、型号、名称搜索，例如 787-9 匹配 B789。 */
  public List<AircraftTypeResponse> searchAircraftTypes(String query, int limit) {
    Like like = Like.of(query);
    return jdbc.sql(
            """
            SELECT icao_code, manufacturer, family, model, display_name, body FROM aircraft_types
            WHERE icao_code LIKE :contains ESCAPE '\\' OR model LIKE :contains ESCAPE '\\'
               OR display_name LIKE :contains ESCAPE '\\' OR family LIKE :contains ESCAPE '\\'
            ORDER BY
              CASE
                WHEN icao_code = :exact THEN 0
                WHEN model LIKE :exactLike ESCAPE '\\' THEN 1
                WHEN icao_code LIKE :prefix ESCAPE '\\' THEN 2
                WHEN model LIKE :prefix ESCAPE '\\' THEN 3
                ELSE 4
              END,
              display_name
            LIMIT :limit
            """)
        .param("exact", like.exact())
        .param("exactLike", like.exactLike())
        .param("prefix", like.prefix())
        .param("contains", like.contains())
        .param("limit", limit)
        .query(ReferenceRepository::aircraftType)
        .list();
  }

  public Optional<AircraftTypeResponse> findAircraftType(String icaoCode) {
    return jdbc.sql(
            """
            SELECT icao_code, manufacturer, family, model, display_name, body FROM aircraft_types
            WHERE icao_code = :icao
            """)
        .param("icao", icaoCode)
        .query(ReferenceRepository::aircraftType)
        .optional();
  }

  private static AirportResponse airport(ResultSet rs, int rowNum) throws SQLException {
    return new AirportResponse(
        rs.getString("iata"),
        rs.getString("icao"),
        rs.getString("name"),
        rs.getString("city"),
        rs.getString("country_code"),
        rs.getDouble("lat"),
        rs.getDouble("lon"),
        rs.getString("tz"));
  }

  private static AirlineResponse airline(ResultSet rs, int rowNum) throws SQLException {
    return new AirlineResponse(
        rs.getString("iata"),
        rs.getString("icao"),
        rs.getString("name"),
        rs.getString("country_code"));
  }

  private static AircraftTypeResponse aircraftType(ResultSet rs, int rowNum) throws SQLException {
    return new AircraftTypeResponse(
        rs.getString("icao_code"),
        rs.getString("manufacturer"),
        rs.getString("family"),
        rs.getString("model"),
        rs.getString("display_name"),
        rs.getString("body"));
  }

  /** LIKE 参数：转义 % _ \，避免用户输入被当成通配符。 */
  private record Like(String exact, String exactLike, String prefix, String contains) {
    static Like of(String query) {
      String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      return new Like(query.toUpperCase(Locale.ROOT), escaped, escaped + "%", "%" + escaped + "%");
    }
  }
}
