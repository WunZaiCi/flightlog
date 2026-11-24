package com.flightlog.auth;

import com.flightlog.common.ProblemResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * 认证规则：
 *
 * <ul>
 *   <li>/api/** 默认要求登录（健康检查、功能开关、注册和登录除外），未登录返回 401 ProblemDetail。
 *   <li>其他路径是前端静态页面，全部公开；前端自己根据 /api/auth/me 做登录门禁。
 *   <li>CSRF：Cookie（XSRF-TOKEN，JS 可读）+ 请求头 X-XSRF-TOKEN，适合单页应用。
 * </ul>
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityContextRepository securityContextRepository,
      ProblemResponseWriter problems)
      throws Exception {
    http.authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.GET, "/api/health", "/api/config")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                .permitAll()
                .requestMatchers("/api", "/api/**")
                .authenticated()
                .anyRequest()
                .permitAll())
        .csrf(csrf -> csrf.spa())
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        .requestCache(cache -> cache.disable())
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(logout ->
            logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(
                    new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
        .exceptionHandling(errors ->
            errors
                .authenticationEntryPoint((request, response, ex) ->
                    problems.write(request, response, HttpStatus.UNAUTHORIZED, "请先登录"))
                .accessDeniedHandler((request, response, ex) ->
                    problems.write(
                        request,
                        response,
                        HttpStatus.FORBIDDEN,
                        ex instanceof CsrfException ? "页面已过期，请刷新后重试" : "没有权限执行此操作")));
    return http.build();
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(
      AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }
}
