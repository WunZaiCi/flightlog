package com.flightlog.reference;

import com.flightlog.reference.ReferenceDtos.AircraftTypeResponse;
import com.flightlog.reference.ReferenceDtos.AirlineResponse;
import com.flightlog.reference.ReferenceDtos.AirportResponse;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;

@Service
public class ReferenceService {

  private static final int MAX_QUERY_LENGTH = 50;
  private static final int DEFAULT_LIMIT = 10;

  private final ReferenceRepository repository;

  public ReferenceService(ReferenceRepository repository) {
    this.repository = repository;
  }

  public List<AirportResponse> searchAirports(String query) {
    return search(query, repository::searchAirports);
  }

  public List<AirlineResponse> searchAirlines(String query) {
    return search(query, repository::searchAirlines);
  }

  public List<AircraftTypeResponse> searchAircraftTypes(String query) {
    return search(query, repository::searchAircraftTypes);
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
