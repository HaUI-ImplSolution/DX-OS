// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.util.Locale;
import java.util.Optional;

// The kinds of value a field holds.
public enum FieldType {
  TEXT,
  NUMBER,
  DATETIME,
  CHOICE,
  REFERENCE,
  USER,
  UNIT,
  FILE,
  BOOLEAN,
  DATE;

  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Optional<FieldType> of(String key) {
    for (FieldType type : values()) {
      if (type.key().equals(key)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}
