package com.flightlog.auth;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 登录用户。保存在会话里（Spring Session JDBC 会序列化它），所以必须可序列化，认证后清掉密码哈希。
 *
 * <p>所有按用户过滤的查询都用 {@link #id()}。
 */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer {

  @Serial private static final long serialVersionUID = 1L;

  private static final List<GrantedAuthority> AUTHORITIES =
      List.of(new SimpleGrantedAuthority("ROLE_USER"));

  private final long id;
  private final String email;
  private String passwordHash;

  AppUserPrincipal(long id, String email, String passwordHash) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
  }

  public long id() {
    return id;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return AUTHORITIES;
  }

  @Override
  public void eraseCredentials() {
    passwordHash = null;
  }
}
