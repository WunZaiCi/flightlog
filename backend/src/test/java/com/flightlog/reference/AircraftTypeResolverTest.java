package com.flightlog.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightlog.reference.ReferenceDtos.AircraftTypeResponse;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 用真实的 refdata/aircraft_types.json，确保换算出的代码都在机型表里。 */
class AircraftTypeResolverTest {

  private record Seed(
      String icao,
      String manufacturer,
      String family,
      String model,
      String displayName,
      String body) {}

  private static final List<AircraftTypeResponse> TYPES = load();

  private static List<AircraftTypeResponse> load() {
    try (InputStream in = new ClassPathResource("refdata/aircraft_types.json").getInputStream()) {
      List<Seed> seeds = JsonMapper.builder().build().readValue(in, new TypeReference<>() {});
      return seeds.stream()
          .map(s ->
              new AircraftTypeResponse(
                  s.icao(), s.manufacturer(), s.family(), s.model(), s.displayName(), s.body()))
          .toList();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static String resolve(String text) {
    return AircraftTypeResolver.resolve(text, TYPES).orElse(null);
  }

  @ParameterizedTest
  @CsvSource({
    // 已有的写法
    "B789, B789",
    "Boeing 787-9, B789",
    "787-9, B789",
    "Airbus A350-900, A359",
    "a20n, A20N",
    // 空客完整子型号：短横后第一位数字是系列
    "A320-212, A320",
    "Airbus A320-214, A320",
    "A320-251N, A20N",
    "A321-271NX, A21N",
    "A321-211, A321",
    "A319-112, A319",
    "A330-343, A333",
    "A330-243, A332",
    "A330-941, A339",
    "A340-642, A346",
    "A350-941, A359",
    "A350-1041, A35K",
    "A380-841, A388",
    "Airbus A380-861, A388",
    // 波音：短横后第一位数字是系列，后面是客户代码
    "737-8AS, B738",
    "Boeing 737-86N, B738",
    "737-7H4, B737",
    "737-9GP(ER), B739",
    "777-3ZG(ER), B77W",
    "777-31H, B773",
    "777-281, B772",
    "777-2Q8LR, B77L",
    "787-881, B788",
    "787-9, B789",
    "787-10, B78X",
    "747-446, B744",
    "747-8F, B748",
    "767-381(ER), B763",
    "757-236, B752",
    // 不带客户代码的 737-7/8/9/10 是 737 MAX
    "737-8, B38M",
    "Boeing 737-8, B38M",
    "737-9, B39M",
    "737 MAX 8, B38M",
    "777-9, B779",
    // 中文行程单和截图里常见的写法
    "B787-9, B789",
    "波音787-9, B789",
    "波音 777-300ER, B77W",
    "空客A320, A320",
    "空中客车 A350-900, A359",
    "A320–212, A320",
    "A321－271NX, A21N"
  })
  void resolvesToIcao(String text, String expected) {
    assertThat(resolve(text)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Boeing 777", "777", "Airbus A350", "Comac C939", "", "B999"})
  void ambiguousOrUnknownIsEmpty(String text) {
    assertThat(resolve(text)).isNull();
  }

  @Test
  void resolveOrFamilyPrefersCodeThenFamily() {
    assertThat(AircraftTypeResolver.resolveOrFamily("A320-212", TYPES))
        .isEqualTo(new AircraftTypeResolver.Resolution("A320", null));
    for (String family : List.of("777", "B777", "Boeing 777", "波音777")) {
      assertThat(AircraftTypeResolver.resolveOrFamily(family, TYPES))
          .as(family)
          .isEqualTo(new AircraftTypeResolver.Resolution(null, "777"));
    }
    assertThat(AircraftTypeResolver.resolveOrFamily("B787", TYPES).search()).isEqualTo("787");
    assertThat(AircraftTypeResolver.resolveOrFamily("A350", TYPES).search()).isEqualTo("A350");
    assertThat(AircraftTypeResolver.resolveOrFamily("X9Z", TYPES))
        .isEqualTo(new AircraftTypeResolver.Resolution(null, null));
  }

  @Test
  void familyOnlyGivesSearchTextWhenSeveralModelsMatch() {
    assertThat(AircraftTypeResolver.familySearch("Boeing 777", TYPES)).contains("777");
    assertThat(AircraftTypeResolver.familySearch("Airbus A350", TYPES)).contains("A350");
    assertThat(AircraftTypeResolver.familySearch("Boeing 787-9", TYPES)).isEmpty();
    assertThat(AircraftTypeResolver.familySearch("Comac C939", TYPES)).isEmpty();
  }

  @Test
  void everyResolvedCodeExistsInTheTable() {
    for (String text : List.of("A320-212", "777-3ZG(ER)", "737-8", "A350-1041", "747-8F")) {
      String code = resolve(text);
      assertThat(TYPES).anyMatch(t -> t.icaoCode().equals(code));
    }
  }
}
