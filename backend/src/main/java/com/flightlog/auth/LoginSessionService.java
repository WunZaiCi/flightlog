package com.flightlog.auth;

import com.flightlog.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/** JSON 登录：校验密码，然后把登录状态写进会话（Spring Session JDBC）。 */
@Service
public class LoginSessionService {

  private final AuthenticationManager authenticationManager;
  private final LoginAttemptService loginAttempts;
  private final SecurityContextRepository securityContextRepository;
  private final FindByIndexNameSessionRepository<? extends Session> sessions;
  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();

  public LoginSessionService(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      LoginAttemptService loginAttempts,
      FindByIndexNameSessionRepository<? extends Session> sessions) {
    this.authenticationManager = authenticationManager;
    this.sessions = sessions;
    this.loginAttempts = loginAttempts;
    this.securityContextRepository = securityContextRepository;
  }

  /** 校验邮箱和密码。邮箱不存在和密码错误返回同样的提示，不暴露哪个邮箱注册过。 */
  public AppUserPrincipal login(
      String email, String password, HttpServletRequest request, HttpServletResponse response) {
    String ip = request.getRemoteAddr();
    if (loginAttempts.isLocked(ip)) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请 15 分钟后再试");
    }
    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    } catch (AuthenticationException ex) {
      loginAttempts.recordFailure(ip);
      throw ApiException.unauthorized("邮箱或密码错误");
    }
    loginAttempts.recordSuccess(ip);
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

  /**
   * 改密码后：当前设备换一个新的会话 ID 并保持登录，同一用户在其他设备上的会话全部删除。
   *
   * <p>Spring Session JDBC 按登录名（邮箱）索引会话，所以可以按用户查出所有会话。
   */
  public void logoutOtherSessions(String email, HttpServletRequest request) {
    HttpSession current = request.getSession(false);
    String currentId = current == null ? null : current.getId();
    for (String id : sessions.findByPrincipalName(email).keySet()) {
      if (!id.equals(currentId)) {
        sessions.deleteById(id);
      }
    }
    // 新 ID 要到请求结束时才写进数据库，所以先按旧 ID 删掉其他会话，再换 ID
    if (current != null) {
      request.changeSessionId();
    }
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
