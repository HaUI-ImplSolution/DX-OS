// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.event;

import java.math.BigDecimal;
import java.time.LocalDate;

// Published inside the transaction that stores a spraying report waiting for an engineer; plain
// values only.
public record TreatmentReportedEvent(Long reportId,String parcelCode,String farmerName,String pesticideName,BigDecimal doseAmount,String doseUnit,LocalDate appliedAt,LocalDate harvestLockedUntil){}
