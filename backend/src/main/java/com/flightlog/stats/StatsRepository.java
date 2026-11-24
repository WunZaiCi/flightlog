package com.flightlog.stats;

import com.flightlog.stats.StatsDtos.UnlockedType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 统计查询。每个查询都按 user_id 过滤。 */
@Repository
public class StatsRepository {

  /** 总数类统计。 */
  public record Totals(
      int flightCount,
      int aircraftTypeCount,
      int unrecognizedTypeCount,
      int airlineCount,
      int airportCount,
      long totalDistanceKm) {}

  private final JdbcClient jdbc;

  public StatsRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Totals totals(long userId) {
    return jdbc.sql(
            """
            SELECT COUNT(*) AS flights,
                   COUNT(DISTINCT f.aircraft_type_icao) AS types,
                   COUNT(DISTINCT CASE WHEN f.aircraft_type_icao IS NOT NULL AND t.icao_code IS NULL
                                       THEN f.aircraft_type_icao END) AS unrecognized,
                   COUNT(DISTINCT f.airline_iata) AS airlines,
                   COALESCE(SUM(f.distance_km), 0) AS distance,
                   (SELECT COUNT(*) FROM (
                      SELECT dep_iata FROM flights WHERE user_id = :userId
                      UNION
                      SELECT arr_iata FROM flights WHERE user_id = :userId)) AS airports
            FROM flights f
            LEFT JOIN aircraft_types t ON t.icao_code = f.aircraft_type_icao
            WHERE f.user_id = :userId
            """)
        .param("userId", userId)
        .query((rs, rowNum) ->
            new Totals(
                rs.getInt("flights"),
                rs.getInt("types"),
                rs.getInt("unrecognized"),
                rs.getInt("airlines"),
                rs.getInt("airports"),
                rs.getLong("distance")))
        .single();
  }

  /** 首次乘坐日期在 [since, today] 之间的机型，按首次乘坐日期倒序。 */
  public List<UnlockedType> unlockedBetween(
      long userId, LocalDate since, LocalDate today, int limit) {
    return jdbc.sql(
            """
            WITH firsts AS (
              SELECT f.aircraft_type_icao AS icao, f.flight_date, f.flight_number,
                     ROW_NUMBER() OVER (PARTITION BY f.aircraft_type_icao
                                        ORDER BY f.flight_date, f.id) AS rn
              FROM flights f
              WHERE f.user_id = :userId AND f.aircraft_type_icao IS NOT NULL
            )
            SELECT x.icao, x.flight_date, x.flight_number, t.display_name
            FROM firsts x
            LEFT JOIN aircraft_types t ON t.icao_code = x.icao
            WHERE x.rn = 1 AND x.flight_date >= :since AND x.flight_date <= :today
            ORDER BY x.flight_date DESC, x.icao
            LIMIT :limit
            """)
        .param("userId", userId)
        .param("since", since.toString())
        .param("today", today.toString())
        .param("limit", limit)
        .query((rs, rowNum) ->
            new UnlockedType(
                rs.getString("icao"),
                rs.getString("display_name"),
                rs.getString("display_name") != null,
                LocalDate.parse(rs.getString("flight_date")),
                rs.getString("flight_number")))
        .list();
  }

  /** 每个机型的乘坐次数、里程、首次乘坐。按次数倒序。 */
  public record TypeAggregate(
      String icao,
      int flightCount,
      long distanceKm,
      LocalDate firstFlightDate,
      String firstFlightNumber,
      String manufacturer,
      String family,
      String model,
      String displayName,
      String body) {}

  public List<TypeAggregate> typeAggregates(long userId) {
    return jdbc.sql(
            """
            WITH firsts AS (
              SELECT aircraft_type_icao AS icao, flight_date, flight_number,
                     ROW_NUMBER() OVER (PARTITION BY aircraft_type_icao ORDER BY flight_date, id) AS rn
              FROM flights WHERE user_id = :userId AND aircraft_type_icao IS NOT NULL
            ),
            agg AS (
              SELECT aircraft_type_icao AS icao, COUNT(*) AS cnt, SUM(distance_km) AS dist
              FROM flights WHERE user_id = :userId AND aircraft_type_icao IS NOT NULL
              GROUP BY aircraft_type_icao
            )
            SELECT agg.icao, agg.cnt, agg.dist, x.flight_date, x.flight_number,
                   t.manufacturer, t.family, t.model, t.display_name, t.body
            FROM agg
            JOIN firsts x ON x.icao = agg.icao AND x.rn = 1
            LEFT JOIN aircraft_types t ON t.icao_code = agg.icao
            ORDER BY agg.cnt DESC, COALESCE(t.display_name, agg.icao)
            """)
        .param("userId", userId)
        .query((rs, rowNum) ->
            new TypeAggregate(
                rs.getString("icao"),
                rs.getInt("cnt"),
                rs.getLong("dist"),
                LocalDate.parse(rs.getString("flight_date")),
                rs.getString("flight_number"),
                rs.getString("manufacturer"),
                rs.getString("family"),
                rs.getString("model"),
                rs.getString("display_name"),
                rs.getString("body")))
        .list();
  }

  /** 机型 → 航司 → 次数。 */
  public record TypeAirline(String icao, String iata, String name, int count) {}

  public List<TypeAirline> airlinesByType(long userId) {
    return jdbc.sql(
            """
            SELECT f.aircraft_type_icao AS icao, f.airline_iata AS iata, COUNT(*) AS cnt,
                   (SELECT al.name FROM airlines al WHERE al.iata = f.airline_iata
                     ORDER BY CASE WHEN al.icao IS NULL THEN 1 ELSE 0 END, al.id LIMIT 1) AS name
            FROM flights f
            WHERE f.user_id = :userId AND f.aircraft_type_icao IS NOT NULL AND f.airline_iata IS NOT NULL
            GROUP BY f.aircraft_type_icao, f.airline_iata
            ORDER BY cnt DESC, iata
            """)
        .param("userId", userId)
        .query((rs, rowNum) ->
            new TypeAirline(
                rs.getString("icao"), rs.getString("iata"), rs.getString("name"), rs.getInt("cnt")))
        .list();
  }

  /** 机型 → 注册号 → 次数（同一架飞机坐过多次）。 */
  public record TypeRegistration(String icao, String registration, int count) {}

  public List<TypeRegistration> registrationsByType(long userId) {
    return jdbc.sql(
            """
            SELECT aircraft_type_icao AS icao, registration, COUNT(*) AS cnt
            FROM flights
            WHERE user_id = :userId AND aircraft_type_icao IS NOT NULL AND registration IS NOT NULL
            GROUP BY aircraft_type_icao, registration
            ORDER BY cnt DESC, registration
            """)
        .param("userId", userId)
        .query((rs, rowNum) ->
            new TypeRegistration(
                rs.getString("icao"), rs.getString("registration"), rs.getInt("cnt")))
        .list();
  }

  /** 年份 → 机身类型（narrow/wide/regional/unknown）→ 航班次数。 */
  public record YearBody(String year, String body, int count) {}

  public List<YearBody> bodyByYear(long userId) {
    return jdbc.sql(
            """
            SELECT substr(f.flight_date, 1, 4) AS yr, COALESCE(t.body, 'unknown') AS body, COUNT(*) AS cnt
            FROM flights f
            LEFT JOIN aircraft_types t ON t.icao_code = f.aircraft_type_icao
            WHERE f.user_id = :userId
            GROUP BY yr, body
            ORDER BY yr
            """)
        .param("userId", userId)
        .query((rs, rowNum) ->
            new YearBody(rs.getString("yr"), rs.getString("body"), rs.getInt("cnt")))
        .list();
  }
}
