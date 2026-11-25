package com.flightlog.reference;

import com.flightlog.reference.ReferenceDtos.AircraftTypeResponse;
import com.flightlog.reference.ReferenceDtos.AirlineResponse;
import com.flightlog.reference.ReferenceDtos.AirportResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;

@Service
public class ReferenceService {

  private static final int MAX_QUERY_LENGTH = 50;
  private static final int DEFAULT_LIMIT = 10;

  private final ReferenceRepository repository;
  private final AircraftTypeResolver resolver;

  public ReferenceService(ReferenceRepository repository, AircraftTypeResolver resolver) {
    this.repository = repository;
    this.resolver = resolver;
  }

  public List<AirportResponse> searchAirports(String query) {
    return search(query, repository::searchAirports);
  }

  public List<AirlineResponse> searchAirlines(String query) {
    return search(query, repository::searchAirlines);
  }

  /** 除了按名称搜索，还能识别完整子型号（A320-212、777-3ZG(ER)），换算出的机型排在第一位。 */
  public List<AircraftTypeResponse> searchAircraftTypes(String query) {
    List<AircraftTypeResponse> results = search(query, repository::searchAircraftTypes);
    Optional<AircraftTypeResponse> resolved =
        resolver.resolve(query == null ? "" : query.strip()).flatMap(repository::findAircraftType);
    if (resolved.isEmpty()) {
      return results;
    }
    List<AircraftTypeResponse> merged = new ArrayList<>();
    merged.add(resolved.get());
    results.stream()
        .filter(t -> !t.icaoCode().equals(resolved.get().icaoCode()))
        .limit(DEFAULT_LIMIT - 1)
        .forEach(merged::add);
    return merged;
  }

  private static <T> List<T> search(String query, BiFunction<String, Integer, List<T>> finder) {
    String q = query == null ? "" : query.strip();
    if (q.isEmpty()) {
      return List.of();
    }
    if (q.length() > MAX_QUERY_LENGTH) {
      q = q.substring(0, MAX_QUERY_LENGTH);
    }
    return finder.apply(q, DEFAULT_LIMIT);
  }
}
