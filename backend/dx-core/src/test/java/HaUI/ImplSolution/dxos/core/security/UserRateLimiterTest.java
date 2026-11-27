// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UserRateLimiterTest {

  private static final long T0 = 1_000_000L;

  @Test
  void callsUpToTheLimitAreAdmitted() {
    UserRateLimiter limiter = new UserRateLimiter();

    assertEquals(0, limiter.admit("u", 3, T0));
    assertEquals(0, limiter.admit("u", 3, T0 + 100));
    assertEquals(0, limiter.admit("u", 3, T0 + 200));
  }

  @Test
  void theCallOverTheLimitSaysHowLongToWait() {
    UserRateLimiter limiter = new UserRateLimiter();
    limiter.admit("u", 1, T0);

    assertEquals(60, limiter.admit("u", 1, T0));
    assertEquals(30, limiter.admit("u", 1, T0 + 30_000));
    assertEquals(1, limiter.admit("u", 1, T0 + 59_500));
  }

  @Test
  void theWindowOpensAgainAfterAMinute() {
    UserRateLimiter limiter = new UserRateLimiter();
    limiter.admit("u", 1, T0);

    assertEquals(0, limiter.admit("u", 1, T0 + 60_000));
    assertEquals(60, limiter.admit("u", 1, T0 + 60_000));
  }

  @Test
  void eachUserHasItsOwnWindow() {
    UserRateLimiter limiter = new UserRateLimiter();
    limiter.admit("ann", 1, T0);

    assertEquals(0, limiter.admit("bob", 1, T0));
    assertEquals(60, limiter.admit("ann", 1, T0));
  }

  @Test
  void aLimitOfZeroAdmitsNobody() {
    assertEquals(60, new UserRateLimiter().admit("u", 0, T0));
  }

  @Test
  void manyUsersAreForgottenOnceTheirWindowIsOver() {
    UserRateLimiter limiter = new UserRateLimiter();
    for (int i = 0; i <= 10_000; i++) {
      limiter.admit("user-" + i, 1, T0);
    }

    assertEquals(0, limiter.admit("user-0", 1, T0 + 120_000));
  }
}
