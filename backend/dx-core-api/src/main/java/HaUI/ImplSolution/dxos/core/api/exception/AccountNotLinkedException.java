// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.exception;

// The signed-in account is not linked to a workspace yet, so there is nothing it may act on.
public class AccountNotLinkedException extends RuntimeException {

  public AccountNotLinkedException(String message) {
    super(message);
  }
}
