// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.exception;

import java.util.Map;
import java.util.stream.Collectors;

// A record that breaks the model, with the problem of each field.
public class ModelValidationException extends RuntimeException {

  private final Map<String, String> fieldErrors;

  public ModelValidationException(Map<String, String> fieldErrors) {
    super(fieldErrors.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue())
        .collect(Collectors.joining(", ")));
    this.fieldErrors = Map.copyOf(fieldErrors);
  }

  public ModelValidationException(String field, String message) {
    this(Map.of(field, message));
  }

  public Map<String, String> fieldErrors() {
    return fieldErrors;
  }
}
