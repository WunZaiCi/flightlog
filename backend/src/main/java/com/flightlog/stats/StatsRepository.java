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
}
