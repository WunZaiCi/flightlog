package com.flightlog.reference;

/** 参考数据接口的响应。 */
public final class ReferenceDtos {

  private ReferenceDtos() {}

  public record AirportResponse(
      String iata,
      String icao,
      String name,
      String city,
      String countryCode,
      double lat,
      double lon,
      String tz) {}

  public record AirlineResponse(String iata, String icao, String name, String countryCode) {}

  public record AircraftTypeResponse(
      String icaoCode,
      String manufacturer,
      String family,
      String model,
      String displayName,
      String body) {}
}
