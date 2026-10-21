package HaUI.ImplSolution.dxos.farm.event;

import java.time.Instant;

// Published inside the transaction of the reminder job for a spraying report that still waits for
// an engineer; plain values only.
public record TreatmentConfirmationOverdueEvent(Long reportId,String parcelCode,String farmerName,String pesticideName,Instant reportedAt){}
