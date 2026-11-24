package com.flightlog.common;

/** 地理计算。 */
public final class Geo {

  /** 地球平均半径（IUGG），公里。 */
  private static final double EARTH_RADIUS_KM = 6371.0088;

  private Geo() {}

  /** 两点之间的大圆距离（haversine 公式），四舍五入到公里。 */
  public static int distanceKm(double lat1, double lon1, double lat2, double lon2) {
    double phi1 = Math.toRadians(lat1);
    double phi2 = Math.toRadians(lat2);
    double dPhi = Math.toRadians(lat2 - lat1);
    double dLambda = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
            + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return (int) Math.round(EARTH_RADIUS_KM * c);
  }
}
