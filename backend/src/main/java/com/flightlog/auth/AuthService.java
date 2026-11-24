package com.flightlog.auth;

import com.flightlog.auth.AuthDtos.RegisterRequest;
import com.flightlog.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  /** bcrypt 只使用前 72 个字节，超出部分会被忽略，所以直接拒绝。 */
  private static final int BCRYPT_MAX_BYTES = 72;

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  public UserAccount register(RegisterRequest request) {
    if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
      throw ApiException.badRequest("密码太长，请减少中文或特殊字符");
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
}
