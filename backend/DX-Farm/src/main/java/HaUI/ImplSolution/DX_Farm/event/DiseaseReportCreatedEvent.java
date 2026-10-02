package HaUI.ImplSolution.DX_Farm.event;

import java.time.Instant;

// Published inside the transaction that stores a new disease report; plain values only.
public record DiseaseReportCreatedEvent(Long reportId,String parcelCode,String riceVariety,String reportedBy,Instant reportedAt){}
