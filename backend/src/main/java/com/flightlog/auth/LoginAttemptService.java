package com.flightlog.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 登录频率限制：同一 IP 连续失败 5 次后锁定 15 分钟；登录成功清零。只在内存里计数（重启后清空），不引入额外依赖。
 *
 * <p>IP 来自 request.getRemoteAddr()；经 Tailscale serve 转发时，Spring 的 ForwardedHeaderFilter
 * （server.forward-headers-strategy=framework）会换成 X-Forwarded-For 里的真实地址。 服务只监听本机，所以这个请求头只可能来自本机的
 * Tailscale。
 */
@Service
public class LoginAttemptService {

  static final int MAX_FAILURES = 5;
  static final Duration LOCK_DURATION = Duration.ofMinutes(15);

  /** 防止内存无限增长：记录太多时清理已经过期的。 */
  private static final int CLEANUP_THRESHOLD = 10_000;

  private record Attempts(int failures, Instant lockedUntil) {}

  private final Clock clock;
  private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

  public LoginAttemptService(Clock clock) {
    this.clock = clock;
  }

  public boolean isLocked(String ip) {
    Attempts a = attempts.get(ip);
    return a != null && a.lockedUntil() != null && clock.instant().isBefore(a.lockedUntil());
  }

  public void recordFailure(String ip) {
    Instant now = clock.instant();
    attempts.compute(
        ip,
        (k, a) -> {
          // 锁定已过期：重新计数
          if (a == null || (a.lockedUntil() != null && !now.isBefore(a.lockedUntil()))) {
            a = new Attempts(0, null);
          }
          int failures = a.failures() + 1;
          return new Attempts(failures, failures >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null);
        });
    if (attempts.size() > CLEANUP_THRESHOLD) {
      attempts
          .entrySet()
          .removeIf(
              e -> e.getValue().lockedUntil() != null && !now.isBefore(e.getValue().lockedUntil()));
    }
  }

  public void recordSuccess(String ip) {
    attempts.remove(ip);
  }
}
