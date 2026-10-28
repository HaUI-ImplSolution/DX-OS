// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.storage;

// The virus scanner is configured but gave no verdict; the file is not stored.
public class ScannerUnavailableException extends RuntimeException {

  public ScannerUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
