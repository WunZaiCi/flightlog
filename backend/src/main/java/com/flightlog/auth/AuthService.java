package com.flightlog.auth;

import com.flightlog.auth.AuthDtos.ChangePasswordRequest;
import com.flightlog.auth.AuthDtos.RegisterRequest;
import com.flightlog.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  /** bcrypt 只使用前 72 个字节，超出部分会被忽略，所以直接拒绝。 */
  private static final int BCRYPT_MAX_BYTES = 72;

  private static final String TOO_LONG = "密码太长，请减少中文或特殊字符";

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttempts;

  public AuthService(
      UserRepository users, PasswordEncoder passwordEncoder, LoginAttemptService loginAttempts) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.loginAttempts = loginAttempts;
  }

  public UserAccount register(RegisterRequest request) {
    if (tooLongForBcrypt(request.password())) {
      throw ApiException.badRequest(TOO_LONG);
    }
    String email = request.email();
    if (users.existsByEmail(email)) {
      throw ApiException.conflict("该邮箱已注册");
    }
    String displayName =
        request.displayName() == null || request.displayName().isBlank()
            ? email.substring(0, email.indexOf('@'))
            : request.displayName();
    try {
      return users.insert(
          email, passwordEncoder.encode(request.password()), displayName, Instant.now());
    } catch (DataAccessException ex) {
      // 两个请求同时注册同一邮箱时，唯一约束会拦住后一个
      if (users.existsByEmail(email)) {
        throw ApiException.conflict("该邮箱已注册");
      }
      throw ex;
    }
  }

  public UserAccount currentUser(long userId) {
    return users.findById(userId).orElseThrow(() -> ApiException.unauthorized("请先登录"));
  }

  /** 验证当前密码后换成新密码。当前密码输错和登录失败一起计数（同一 IP 连续 5 次锁定 15 分钟）， 防止有人拿到已登录的设备后猜密码。 */
  public UserAccount changePassword(long userId, ChangePasswordRequest request, String ip) {
    if (loginAttempts.isLocked(ip)) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "密码输错次数过多，请 15 分钟后再试");
    }
    UserAccount user = currentUser(userId);
    if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
      loginAttempts.recordFailure(ip);
      throw ApiException.invalidField("currentPassword", "当前密码不正确");
    }
    loginAttempts.recordSuccess(ip);
    if (request.newPassword().equals(request.currentPassword())) {
      throw ApiException.invalidField("newPassword", "新密码不能和当前密码相同");
    }
    if (tooLongForBcrypt(request.newPassword())) {
      throw ApiException.invalidField("newPassword", TOO_LONG);
    }
    users.updatePasswordHash(userId, passwordEncoder.encode(request.newPassword()));
    return user;
  }

  private static boolean tooLongForBcrypt(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
  }
}
