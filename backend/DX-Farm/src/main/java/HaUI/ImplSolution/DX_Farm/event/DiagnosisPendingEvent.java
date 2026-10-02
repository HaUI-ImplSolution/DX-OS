package HaUI.ImplSolution.DX_Farm.event;

import java.math.BigDecimal;

// Published inside the transaction that stores a PENDING diagnosis; plain values only.
public record DiagnosisPendingEvent(Long reportId,String parcelCode,String diseaseCode,String diseaseName,BigDecimal confidence,boolean hasTreatmentRule){}
