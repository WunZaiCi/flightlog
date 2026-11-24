package com.flightlog.flight;

import com.flightlog.flight.FlightDtos.AircraftTypeSummary;
import com.flightlog.flight.FlightDtos.AirportSummary;
import com.flightlog.flight.FlightDtos.FlightResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 航班表。每个方法都要求 userId，所有查询都按 user_id 过滤。 */
@Repository
public class FlightRepository {

  /** 要写入的航班数据（已规范化、已算好距离）。 */
  public record FlightWrite(
      LocalDate flightDate,
      String flightNumber,
      String airlineIata,
      String depIata,
      String arrIata,
      String aircraftTypeIcao,
      String registration,
      int distanceKm,
      String notes,
      String source) {}

  private static final String SELECT =
      """
      SELECT f.id, f.flight_date, f.flight_number, f.airline_iata, f.aircraft_type_icao,
             f.registration, f.distance_km, f.notes, f.source, f.created_at, f.updated_at,
             (SELECT al.name FROM airlines al WHERE al.iata = f.airline_iata
               ORDER BY CASE WHEN al.icao IS NULL THEN 1 ELSE 0 END, al.id LIMIT 1) AS airline_name,
             d.iata AS dep_iata, d.name AS dep_name, d.city AS dep_city,
             d.country_code AS dep_country, d.lat AS dep_lat, d.lon AS dep_lon,
             a.iata AS arr_iata, a.name AS arr_name, a.city AS arr_city,
             a.country_code AS arr_country, a.lat AS arr_lat, a.lon AS arr_lon,
             t.icao_code AS type_icao, t.manufacturer AS type_manufacturer, t.family AS type_family,
             t.model AS type_model, t.display_name AS type_display_name, t.body AS type_body
      FROM flights f
      JOIN airports d ON d.iata = f.dep_iata
      JOIN airports a ON a.iata = f.arr_iata
      LEFT JOIN aircraft_types t ON t.icao_code = f.aircraft_type_icao
      """;

  private final JdbcClient jdbc;

  public FlightRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public long insert(long userId, FlightWrite w, Instant now) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO flights (user_id, flight_date, flight_number, airline_iata, dep_iata, arr_iata,
                                 aircraft_type_icao, registration, distance_km, notes, source,
                                 created_at, updated_at)
            VALUES (:userId, :flightDate, :flightNumber, :airlineIata, :depIata, :arrIata,
                    :aircraftTypeIcao, :registration, :distanceKm, :notes, :source, :now, :now)
            """)
        .param("userId", userId)
        .params(writeParams(w))
        .param("now", now.toString())
        .update(keys);
    return keys.getKey().longValue();
  }

  /** 返回更新的行数；0 表示航班不存在或不属于该用户。 */
  public int update(long userId, long id, FlightWrite w, Instant now) {
    return jdbc.sql(
            """
            UPDATE flights SET flight_date = :flightDate, flight_number = :flightNumber,
                   airline_iata = :airlineIata, dep_iata = :depIata, arr_iata = :arrIata,
                   aircraft_type_icao = :aircraftTypeIcao, registration = :registration,
                   distance_km = :distanceKm, notes = :notes, source = :source, updated_at = :now
            WHERE id = :id AND user_id = :userId
            """)
        .params(writeParams(w))
        .param("now", now.toString())
        .param("id", id)
        .param("userId", userId)
        .update();
  }

  public int delete(long userId, long id) {
    return jdbc.sql("DELETE FROM flights WHERE id = :id AND user_id = :userId")
        .param("id", id)
        .param("userId", userId)
        .update();
  }

  public Optional<FlightResponse> findById(long userId, long id) {
    return jdbc.sql(SELECT + " WHERE f.user_id = :userId AND f.id = :id")
        .param("userId", userId)
        .param("id", id)
        .query(FlightRepository::map)
        .optional();
  }

  public List<FlightResponse> list(long userId) {
    return jdbc.sql(SELECT + " WHERE f.user_id = :userId ORDER BY f.flight_date DESC, f.id DESC")
        .param("userId", userId)
        .query(FlightRepository::map)
        .list();
  }

  /** 同一用户、同一天、同一航班号的其他航班。 */
  public List<Long> findDuplicateIds(
      long userId, LocalDate date, String flightNumber, Long excludeId) {
    return jdbc.sql(
            """
            SELECT id FROM flights
            WHERE user_id = :userId AND flight_date = :date AND flight_number = :number
              AND (:excludeId IS NULL OR id <> :excludeId)
            ORDER BY id
            """)
        .param("userId", userId)
        .param("date", date.toString())
        .param("number", flightNumber)
        .param("excludeId", excludeId)
        .query(Long.class)
        .list();
  }

  /** 可能含 null（可选字段），所以用 HashMap 而不是 Map.of。 */
  private static Map<String, Object> writeParams(FlightWrite w) {
    Map<String, Object> p = new HashMap<>();
    p.put("flightDate", w.flightDate().toString());
    p.put("flightNumber", w.flightNumber());
    p.put("airlineIata", w.airlineIata());
    p.put("depIata", w.depIata());
    p.put("arrIata", w.arrIata());
    p.put("aircraftTypeIcao", w.aircraftTypeIcao());
    p.put("registration", w.registration());
    p.put("distanceKm", w.distanceKm());
    p.put("notes", w.notes());
    p.put("source", w.source());
    return p;
  }

  private static FlightResponse map(ResultSet rs, int rowNum) throws SQLException {
    String typeIcao = rs.getString("type_icao");
    AircraftTypeSummary type =
        typeIcao == null
            ? null
            : new AircraftTypeSummary(
                typeIcao,
                rs.getString("type_manufacturer"),
                rs.getString("type_family"),
                rs.getString("type_model"),
                rs.getString("type_display_name"),
                rs.getString("type_body"));
    String storedType = rs.getString("aircraft_type_icao");
    return new FlightResponse(
        rs.getLong("id"),
        LocalDate.parse(rs.getString("flight_date")),
        rs.getString("flight_number"),
        rs.getString("airline_iata"),
        rs.getString("airline_name"),
        airport(rs, "dep"),
        airport(rs, "arr"),
        storedType,
        storedType != null && type != null,
        type,
        rs.getString("registration"),
        rs.getInt("distance_km"),
        rs.getString("notes"),
        rs.getString("source"),
        Instant.parse(rs.getString("created_at")),
        Instant.parse(rs.getString("updated_at")));
  }

  private static AirportSummary airport(ResultSet rs, String prefix) throws SQLException {
    return new AirportSummary(
        rs.getString(prefix + "_iata"),
        rs.getString(prefix + "_name"),
        rs.getString(prefix + "_city"),
        rs.getString(prefix + "_country"),
        rs.getDouble(prefix + "_lat"),
        rs.getDouble(prefix + "_lon"));
  }
}
