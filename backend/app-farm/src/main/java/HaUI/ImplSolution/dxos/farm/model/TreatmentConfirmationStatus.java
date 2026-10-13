// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.model;

public enum TreatmentConfirmationStatus {
  // Reported by the farmer, waiting for an engineer
  PENDING_CONFIRMATION,
  // Confirmed by an engineer, or entered by staff
  CONFIRMED,
  // Engineer did not accept the report; the farmer may report again
  REJECTED
}
