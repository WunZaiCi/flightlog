package com.flightlog.flight;

import com.flightlog.common.ApiException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 航班列表的筛选条件，都可以为空。
 *
 * @param year 年份（按出发日期）
 * @param airline 航司 IATA 代码
 * @param type ICAO 机型代码
 * @param airport 机场 IATA 代码（出发或到达）
 * @param query 关键词：航班号、注册号、机场、城市、机型名、航司名、备注
 */
public record FlightFilter(String year, String airline, String type, String airport, String query) {

  private static final Pattern YEAR = Pattern.compile("^[0-9]{4}$");
  private static final int MAX_QUERY_LENGTH = 50;

  /** 规范化并校验来自请求参数的筛选条件：空字符串当作不筛选，代码转大写。 */
  public static FlightFilter of(
      String year, String airline, String type, String airport, String query) {
    String y = blankToNull(year);
    if (y != null && !YEAR.matcher(y).matches()) {
      throw ApiException.invalidField("year", "年份格式不正确");
    }
    String q = blankToNull(query);
    if (q != null && q.length() > MAX_QUERY_LENGTH) {
      q = q.substring(0, MAX_QUERY_LENGTH);
    }
    return new FlightFilter(y, upper(airline), upper(type), upper(airport), q);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  private static String upper(String s) {
    String v = blankToNull(s);
    return v == null ? null : v.toUpperCase(Locale.ROOT);
  }
}
