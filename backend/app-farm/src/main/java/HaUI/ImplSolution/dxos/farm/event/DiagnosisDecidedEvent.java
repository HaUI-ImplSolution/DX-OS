// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.event;

// Published inside the transaction that approves or rejects a diagnosis; plain values only.
public record DiagnosisDecidedEvent(Long reportId,String parcelCode,String diseaseName,boolean approved,String reason,String decidedBy){}
