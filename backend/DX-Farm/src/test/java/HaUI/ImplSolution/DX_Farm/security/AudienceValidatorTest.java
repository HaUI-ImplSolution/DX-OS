// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

  private static Jwt token(Object audience) {
    Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("u")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    if (audience != null) {
      builder.claim("aud", audience);
    }
    return builder.build();
  }

  private static boolean accepts(Object audience) {
    return !SecurityConfig.audienceValidator("dxfarm-api").validate(token(audience)).hasErrors();
  }

  @Test
  void acceptsTokensIssuedForTheApi() {
    assertThat(accepts(List.of("account", "dxfarm-api"))).isTrue();
  }

  @Test
  void rejectsTokensForAnotherAudience() {
    assertThat(accepts(List.of("account"))).isFalse();
  }

  @Test
  void rejectsTokensWithoutAudience() {
    assertThat(accepts(null)).isFalse();
  }
}
