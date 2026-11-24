package com.flightlog.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoTest {

  @Test
  void samePointIsZero() {
    assertThat(Geo.distanceKm(35.55, 139.78, 35.55, 139.78)).isZero();
  }

  @Test
  void londonHeathrowToNewYorkJfk() {
    // LHR (51.4700, -0.4543) → JFK (40.6398, -73.7789)：大圆距离约 5540 km
    assertThat(Geo.distanceKm(51.4700, -0.4543, 40.6398, -73.7789)).isCloseTo(5540, within(10));
  }

  @Test
  void fukuokaToHaneda() {
    // FUK (33.5859, 130.4510) → HND (35.5523, 139.7800)：约 880 km
    assertThat(Geo.distanceKm(33.5859, 130.4510, 35.5523, 139.7800)).isCloseTo(880, within(10));
  }

  @Test
  void crossingTheDateLineTakesTheShortWay() {
    // NRT (35.7647, 140.3864) → SFO (37.6190, -122.3750)：约 8240 km，不是绕地球另一边
    assertThat(Geo.distanceKm(35.7647, 140.3864, 37.6190, -122.3750)).isCloseTo(8240, within(30));
  }

  @Test
  void isSymmetric() {
    assertThat(Geo.distanceKm(33.5859, 130.4510, 35.5523, 139.7800))
        .isEqualTo(Geo.distanceKm(35.5523, 139.7800, 33.5859, 130.4510));
  }
}
