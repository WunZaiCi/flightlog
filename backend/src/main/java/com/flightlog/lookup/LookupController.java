package com.flightlog.lookup;

import com.flightlog.lookup.FlightLookupService.LookupResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 航班号自动查询（AeroDataBox，带缓存）。结果只用于预填表单。 */
@RestController
@RequestMapping("/api/lookup")
public class LookupController {

  private final FlightLookupService lookupService;

  public LookupController(FlightLookupService lookupService) {
    this.lookupService = lookupService;
  }

  @GetMapping("/flight")
  public LookupResponse flight(
      @RequestParam(defaultValue = "") String number,
      @RequestParam(defaultValue = "") String date) {
    return lookupService.lookup(number, date);
  }
}
