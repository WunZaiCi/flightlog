package com.flightlog.auth;

import com.flightlog.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/** JSON 登录：校验密码，然后把登录状态写进会话（Spring Session JDBC）。 */
@Service
public class LoginSessionService {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();

  public LoginSessionService(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
  }

  /** 校验邮箱和密码。邮箱不存在和密码错误返回同样的提示，不暴露哪个邮箱注册过。 */
  public AppUserPrincipal login(
      String email, String password, HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    } catch (AuthenticationException ex) {
      throw ApiException.unauthorized("邮箱或密码错误");
    }
    startSession(authentication, request, response);
    return (AppUserPrincipal) authentication.getPrincipal();
  }

  /** 注册成功后直接登录。 */
  public void loginAs(UserAccount user, HttpServletRequest request, HttpServletResponse response) {
    AppUserPrincipal principal = new AppUserPrincipal(user.id(), user.email(), null);
    startSession(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities()),
        request,
        response);
  }

  private void startSession(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    // 防会话固定攻击：已有会话时换一个新的会话 ID
    if (request.getSession(false) != null) {
      request.changeSessionId();
    }
    SecurityContext context = contextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    contextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
  }
}
