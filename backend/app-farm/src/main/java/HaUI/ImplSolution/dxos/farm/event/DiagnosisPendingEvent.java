// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.event;

import java.math.BigDecimal;

// Published inside the transaction that stores a PENDING diagnosis; plain values only.
public record DiagnosisPendingEvent(Long reportId,String parcelCode,String diseaseCode,String diseaseName,BigDecimal confidence,boolean hasTreatmentRule){}
