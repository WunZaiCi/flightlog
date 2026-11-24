package com.flightlog.flight;

import com.flightlog.auth.AppUserPrincipal;
import com.flightlog.flight.FlightDtos.FlightRequest;
import com.flightlog.flight.FlightDtos.FlightResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 航班增删改查。只操作当前用户的航班，别人的航班一律 404。 */
@RestController
@RequestMapping("/api/flights")
public class FlightController {

  private final FlightService flightService;

  public FlightController(FlightService flightService) {
    this.flightService = flightService;
  }

  @GetMapping
  public List<FlightResponse> list(@AuthenticationPrincipal AppUserPrincipal user) {
    return flightService.list(user.id());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FlightResponse create(
      @AuthenticationPrincipal AppUserPrincipal user, @Valid @RequestBody FlightRequest body) {
    return flightService.create(user.id(), body);
  }

  @GetMapping("/{id}")
  public FlightResponse get(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id) {
    return flightService.get(user.id(), id);
  }

  @PutMapping("/{id}")
  public FlightResponse update(
      @AuthenticationPrincipal AppUserPrincipal user,
      @PathVariable long id,
      @Valid @RequestBody FlightRequest body) {
    return flightService.update(user.id(), id, body);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id) {
    flightService.delete(user.id(), id);
  }
}
