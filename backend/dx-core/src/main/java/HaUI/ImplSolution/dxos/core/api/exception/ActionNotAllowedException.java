// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.exception;

// The actor may not do this. The reason is a stable code for clients, or null when there is none.
public class ActionNotAllowedException extends RuntimeException {

  private final String reason;
  private final String code;

  public ActionNotAllowedException(String message) {
    this(message, null);
  }

  public ActionNotAllowedException(String message, String reason) {
    this(message, reason, null);
  }

  // The code is the one an outside service gave when it refused the action.
  public ActionNotAllowedException(String message, String reason, String code) {
    super(message);
    this.reason = reason;
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public String getReason() {
    return reason;
  }
}
