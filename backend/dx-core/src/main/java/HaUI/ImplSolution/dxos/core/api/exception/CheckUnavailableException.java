// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.exception;

// The service that checks an action could not be asked or answered wrongly, and the target is set
// to refuse in that case. The record is untouched, so the action can be tried again.
public class CheckUnavailableException extends RuntimeException {

  public CheckUnavailableException(String message) {
    super(message);
  }
}
