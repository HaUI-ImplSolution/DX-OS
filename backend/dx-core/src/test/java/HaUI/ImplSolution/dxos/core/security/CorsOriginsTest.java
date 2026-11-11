// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsOriginsTest {

  @Test
  void acceptsExactHttpsAndLoopbackOrigins() {
    assertThat(
            CorsOrigins.validate(
                List.of(
                    " https://UI.Example.test:8443 ",
                    "https://ui.example.test:443",
                    "http://localhost:80",
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "https://ui.example.test:8443")))
        .containsExactly(
            "https://ui.example.test:8443",
            "https://ui.example.test",
            "http://localhost",
            "http://localhost:5173",
            "http://127.0.0.1:5173");
  }

  @Test
  void rejectsOriginsThatAreBroadOrNotOrigins() {
    for (String origin : List.of("https://*.example.test", "http://ui.example.test",
        "https://user@example.test", "https://example.test/callback",
        "https://example.test?tenant=a", "https://example.test#fragment",
        "https://example.test:99999")) {
      assertThatThrownBy(() -> CorsOrigins.validate(origin)).as(origin)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
