// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.exception;

// The actor may not do this. The reason is a stable code for clients, or null when there is none.
public class ActionNotAllowedException extends RuntimeException {

  private final String reason;

  public ActionNotAllowedException(String message) {
    this(message, null);
  }

  public ActionNotAllowedException(String message, String reason) {
    super(message);
    this.reason = reason;
  }

  public String getReason() {
    return reason;
  }
}
