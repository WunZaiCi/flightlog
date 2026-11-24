package com.flightlog.flight;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FlightNumbersTest {

  @ParameterizedTest
  @CsvSource({
    "nh 242, NH242",
    "NH242, NH242",
    " nh-0242 , NH0242",
    "3u8888, 3U8888",
    "mu 5101, MU5101",
    "ana242, ANA242",
    "ua1a, UA1A"
  })
  void normalizesToUppercaseWithoutSpaces(String input, String expected) {
    assertThat(FlightNumbers.normalize(input)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NH242", "3U8888", "9C8001", "ANA242", "UA1", "BA0001", "AA100A"})
  void acceptsValidFlightNumbers(String number) {
    assertThat(FlightNumbers.isValid(number)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "12", "12345", "NH", "NH12345", "NH-242", "東京242"})
  void rejectsInvalidFlightNumbers(String number) {
    assertThat(FlightNumbers.isValid(number)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"NH242, NH", "3U8888, 3U", "9C8001, 9C"})
  void extractsIataAirlineDesignator(String number, String airline) {
    assertThat(FlightNumbers.iataDesignator(number)).contains(airline);
  }

  @ParameterizedTest
  @CsvSource({"ANA242, ANA", "CCA1234, CCA"})
  void extractsIcaoAirlineDesignator(String number, String airline) {
    assertThat(FlightNumbers.iataDesignator(number)).isEmpty();
    assertThat(FlightNumbers.icaoDesignator(number)).contains(airline);
  }
}
