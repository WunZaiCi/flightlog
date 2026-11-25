package com.flightlog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

  /** 可以手动拨动的时钟。 */
  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-11-25T00:00:00Z");

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MutableClock clock = new MutableClock();
  private final LoginAttemptService attempts = new LoginAttemptService(clock);

  @Test
  void locksAfterFiveConsecutiveFailures() {
    for (int i = 0; i < 4; i++) {
      attempts.recordFailure("1.2.3.4");
      assertThat(attempts.isLocked("1.2.3.4")).isFalse();
    }
    attempts.recordFailure("1.2.3.4");
    assertThat(attempts.isLocked("1.2.3.4")).isTrue();
    assertThat(attempts.isLocked("5.6.7.8")).isFalse();
  }

  @Test
  void unlocksAfterFifteenMinutes() {
    for (int i = 0; i < 5; i++) {
      attempts.recordFailure("1.2.3.4");
    }
    clock.now = clock.now.plus(Duration.ofMinutes(14));
    assertThat(attempts.isLocked("1.2.3.4")).isTrue();
    clock.now = clock.now.plus(Duration.ofMinutes(1));
    assertThat(attempts.isLocked("1.2.3.4")).isFalse();
    // 解锁后重新计数
    attempts.recordFailure("1.2.3.4");
    assertThat(attempts.isLocked("1.2.3.4")).isFalse();
  }

  @Test
  void successResetsTheCounter() {
    for (int i = 0; i < 4; i++) {
      attempts.recordFailure("1.2.3.4");
    }
    attempts.recordSuccess("1.2.3.4");
    for (int i = 0; i < 4; i++) {
      attempts.recordFailure("1.2.3.4");
    }
    assertThat(attempts.isLocked("1.2.3.4")).isFalse();
  }
}
