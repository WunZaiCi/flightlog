package com.flightlog.reference;

import com.flightlog.reference.ReferenceDtos.AircraftTypeResponse;
import com.flightlog.reference.ReferenceDtos.AirlineResponse;
import com.flightlog.reference.ReferenceDtos.AirportResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 表单自动补全用的参考数据搜索。 */
@RestController
@RequestMapping("/api/ref")
public class ReferenceController {

  private final ReferenceService referenceService;

  public ReferenceController(ReferenceService referenceService) {
    this.referenceService = referenceService;
  }

  @GetMapping("/airports")
  public List<AirportResponse> airports(@RequestParam(defaultValue = "") String q) {
    return referenceService.searchAirports(q);
  }

  @GetMapping("/airlines")
  public List<AirlineResponse> airlines(@RequestParam(defaultValue = "") String q) {
    return referenceService.searchAirlines(q);
  }

  @GetMapping("/aircraft-types")
  public List<AircraftTypeResponse> aircraftTypes(@RequestParam(defaultValue = "") String q) {
    return referenceService.searchAircraftTypes(q);
  }
}
