// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.exception;

// The actor does not hold a role the workflow step requires.
public class ActionNotAllowedException extends RuntimeException {

  public ActionNotAllowedException(String message) {
    super(message);
  }
}
