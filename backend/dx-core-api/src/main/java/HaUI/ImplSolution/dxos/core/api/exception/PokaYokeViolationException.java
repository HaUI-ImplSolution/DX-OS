// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.exception;

import java.util.Map;

// A business rule that protects the data refused the action. data: extra values the client shows
// with the error, keyed by field name.
public class PokaYokeViolationException extends RuntimeException {

  private final String errorCode;
  private final Map<String, Object> data;

  public PokaYokeViolationException(String errorCode, String message) {
    this(errorCode, message, Map.of());
  }

  public PokaYokeViolationException(String errorCode, String message, Map<String, Object> data) {
    super(message);
    this.errorCode = errorCode;
    this.data = data == null ? Map.of() : data;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public Map<String, Object> getData() {
    return data;
  }
}
