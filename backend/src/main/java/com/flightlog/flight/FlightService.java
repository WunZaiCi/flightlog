package com.flightlog.flight;

import com.flightlog.common.ApiException;
import com.flightlog.common.Geo;
import com.flightlog.flight.FlightDtos.FlightRequest;
import com.flightlog.flight.FlightDtos.FlightResponse;
import com.flightlog.flight.FlightRepository.FlightWrite;
import com.flightlog.reference.ReferenceDtos.AirlineResponse;
import com.flightlog.reference.ReferenceDtos.AirportResponse;
import com.flightlog.reference.ReferenceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightService {

  private static final LocalDate EARLIEST = LocalDate.of(1950, 1, 1);

  private final FlightRepository flights;
  private final ReferenceRepository reference;

  public FlightService(FlightRepository flights, ReferenceRepository reference) {
    this.flights = flights;
    this.reference = reference;
  }

  public List<FlightResponse> list(long userId, FlightFilter filter) {
    return flights.list(userId, filter);
  }

  public FlightResponse get(long userId, long id) {
    return flights.findById(userId, id).orElseThrow(FlightService::notFound);
  }

  @Transactional
  public FlightResponse create(long userId, FlightRequest request) {
    FlightWrite write = prepare(request);
    checkDuplicates(userId, write, null, request.allowDuplicate());
    long id = flights.insert(userId, write, Instant.now());
    return get(userId, id);
  }

  @Transactional
  public FlightResponse update(long userId, long id, FlightRequest request) {
    FlightResponse existing = get(userId, id);
    FlightWrite write = prepare(request);
    // 日期和航班号都没变时不再提示重复（用户录入时已经确认过）
    boolean keyChanged =
        !existing.flightDate().equals(write.flightDate())
            || !existing.flightNumber().equals(write.flightNumber());
    checkDuplicates(userId, write, id, request.allowDuplicate() || !keyChanged);
    flights.update(userId, id, write, Instant.now());
    return get(userId, id);
  }

  @Transactional
  public void delete(long userId, long id) {
    if (flights.delete(userId, id) == 0) {
      throw notFound();
    }
  }

  /** 校验机场、日期，推出航司，计算大圆距离。 */
  private FlightWrite prepare(FlightRequest request) {
    Map<String, String> errors = new LinkedHashMap<>();

    LocalDate latest = LocalDate.now().plusYears(2);
    if (request.flightDate().isBefore(EARLIEST) || request.flightDate().isAfter(latest)) {
      errors.put("flightDate", "日期超出范围");
    }

    AirportResponse dep = reference.findAirport(request.depIata()).orElse(null);
    if (dep == null) {
      errors.put("depIata", "找不到机场 " + request.depIata());
    }
    AirportResponse arr = reference.findAirport(request.arrIata()).orElse(null);
    if (arr == null) {
      errors.put("arrIata", "找不到机场 " + request.arrIata());
    } else if (request.arrIata().equals(request.depIata())) {
      errors.put("arrIata", "出发和到达机场不能相同");
    }

    if (!errors.isEmpty()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, errors.values().iterator().next(), errors, Map.of());
    }

    return new FlightWrite(
        request.flightDate(),
        request.flightNumber(),
        airlineIata(request.flightNumber()),
        dep.iata(),
        arr.iata(),
        request.aircraftType(),
        request.registration(),
        Geo.distanceKm(dep.lat(), dep.lon(), arr.lat(), arr.lon()),
        request.notes(),
        request.source());
  }

  private String airlineIata(String flightNumber) {
    return FlightNumbers.iataDesignator(flightNumber)
        .or(() ->
            FlightNumbers.icaoDesignator(flightNumber)
                .flatMap(reference::findAirlineByIcao)
                .map(AirlineResponse::iata))
        .orElse(null);
  }

  private void checkDuplicates(long userId, FlightWrite write, Long excludeId, boolean allowed) {
    if (allowed) {
      return;
    }
    List<Long> duplicates =
        flights.findDuplicateIds(userId, write.flightDate(), write.flightNumber(), excludeId);
    if (!duplicates.isEmpty()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "这一天已经录入过 " + write.flightNumber() + "，可能是重复录入",
          Map.of(),
          Map.of("duplicateIds", duplicates));
    }
  }

  private static ApiException notFound() {
    return ApiException.notFound("航班不存在");
  }
}
