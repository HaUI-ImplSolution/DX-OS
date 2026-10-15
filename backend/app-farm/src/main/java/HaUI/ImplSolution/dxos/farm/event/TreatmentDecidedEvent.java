// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.event;

import java.time.LocalDate;

// Published inside the transaction that records, confirms or rejects a spraying; plain values only.
public record TreatmentDecidedEvent(Long reportId,String parcelCode,String pesticideName,Outcome outcome,LocalDate harvestLockedUntil,String reason,String decidedBy){

public enum Outcome {
  // An engineer or administrator recorded the spraying themselves
  RECORDED,
  // The engineer confirmed the farmer's declaration
  CONFIRMED, REJECTED
}}
