// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.exception;

import java.time.LocalDate;

public class PokaYokeViolationException extends RuntimeException {

  private final String errorCode;
  private final LocalDate harvestLockedUntil;

  public PokaYokeViolationException(String errorCode, String message) {
    this(errorCode, message, null);
  }

  public PokaYokeViolationException(String errorCode, String message,
      LocalDate harvestLockedUntil) {
    super(message);
    this.errorCode = errorCode;
    this.harvestLockedUntil = harvestLockedUntil;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public LocalDate getHarvestLockedUntil() {
    return harvestLockedUntil;
  }
}
