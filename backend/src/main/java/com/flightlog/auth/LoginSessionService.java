package com.flightlog.auth;

import com.flightlog.auth.AuthDtos.ChangePasswordRequest;
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
  private final AuthService authService;
  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();

  public LoginSessionService(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      LoginAttemptService loginAttempts,
      FindByIndexNameSessionRepository<? extends Session> sessions,
      AuthService authService) {
    this.authenticationManager = authenticationManager;
    this.authService = authService;
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
   * 修改密码并让其他设备上的登录失效。顺序是：校验 → 删其他会话 → 写新密码。
   *
   * <p>不能放进同一个事务：Spring Session 用自己的事务（另一个连接）删会话，SQLite 只允许一个写入，会互相等待。
   * 按这个顺序，写密码失败时只是其他设备需要重新登录，不会出现「密码改了但其他设备还登录着」。
   */
  public void changePassword(long userId, ChangePasswordRequest body, HttpServletRequest request) {
    UserAccount user = authService.checkPasswordChange(userId, body, request.getRemoteAddr());
    logoutOtherSessions(user.email(), request);
    authService.savePassword(userId, body.newPassword());
  }

  /**
   * 当前设备换一个新的会话 ID 并保持登录，同一用户在其他设备上的会话全部删除。
   *
   * <p>Spring Session JDBC 按登录名（邮箱）索引会话，所以可以按用户查出所有会话。
   */
  private void logoutOtherSessions(String email, HttpServletRequest request) {
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
