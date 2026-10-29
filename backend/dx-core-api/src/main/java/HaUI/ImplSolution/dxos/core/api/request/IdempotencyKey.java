// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.request;

import java.util.UUID;

// The Idempotency-Key header of a request that creates a record.
public final class IdempotencyKey {

  public static final String HEADER = "Idempotency-Key";

  private IdempotencyKey() {}

  // Null when the client sent no key; a key that is not a UUID is a bad request.
  public static UUID parse(String header) {
    if (header == null) {
      return null;
    }
    try {
      return UUID.fromString(header);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(HEADER + " must be a UUID");
    }
  }
}
