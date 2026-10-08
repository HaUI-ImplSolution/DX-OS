// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.exception;

// The signed-in farmer account has no farm household yet, so there is nothing it may act on.
public class AccountNotLinkedException extends RuntimeException {

  public AccountNotLinkedException(String message) {
    super(message);
  }
}
