// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

// The versions of a model and record that a caller based a decision on.
// @formatter:off
public record RecordPreconditions(Long version, Long modelVersion) {

  public static RecordPreconditions from(Map<String, String> body) {
    return new RecordPreconditions(parse(body, "version", 0), parse(body, "modelVersion", 1));
  }

  private static Long parse(Map<String, String> body, String field, long minimum) {
    String raw = body == null ? null : body.get(field);
    if (raw == null) {
      return null;
    }
    try {
      long value = Long.parseLong(raw);
      if (value < minimum) {
        throw new NumberFormatException();
      }
      return value;
    } catch (NumberFormatException error) {
      throw new ModelValidationException(field, "must be a whole number at least " + minimum);
    }
  }

  public static void check(String entity, Long expected, Map<String, Object> record) {
    if (expected != null && expected.longValue() != ((Number) record.get("version")).longValue()) {
      throw new ObjectOptimisticLockingFailureException(entity, expected);
    }
  }
}
// @formatter:on
