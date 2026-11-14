// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.exception;

// The thing is in a state that refuses this. The reason is a stable code for clients, or null.
public class InvalidStateException extends RuntimeException {

  private final String reason;

  public InvalidStateException(String message) {
    this(message, null);
  }

  public InvalidStateException(String message, String reason) {
    super(message);
    this.reason = reason;
  }

  public String getReason() {
    return reason;
  }
}
