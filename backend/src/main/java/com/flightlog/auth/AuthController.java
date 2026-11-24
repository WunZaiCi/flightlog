package com.flightlog.auth;

import com.flightlog.auth.AuthDtos.LoginRequest;
import com.flightlog.auth.AuthDtos.RegisterRequest;
import com.flightlog.auth.AuthDtos.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 注册、登录、当前用户。登出由 Spring Security 处理（POST /api/auth/logout）。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final LoginSessionService loginSessionService;

  public AuthController(AuthService authService, LoginSessionService loginSessionService) {
    this.authService = authService;
    this.loginSessionService = loginSessionService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(
      @Valid @RequestBody RegisterRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    UserAccount user = authService.register(body);
    loginSessionService.loginAs(user, request, response);
    return UserResponse.of(user);
  }

  @PostMapping("/login")
  public UserResponse login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    AppUserPrincipal principal =
        loginSessionService.login(body.email(), body.password(), request, response);
    return UserResponse.of(authService.currentUser(principal.id()));
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
    return UserResponse.of(authService.currentUser(principal.id()));
  }
}
