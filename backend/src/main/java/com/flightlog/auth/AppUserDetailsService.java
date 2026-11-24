package com.flightlog.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  public AppUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String email) {
    return users
        .findByEmail(Emails.normalize(email))
        .map(u -> new AppUserPrincipal(u.id(), u.email(), u.passwordHash()))
        .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
  }
}
