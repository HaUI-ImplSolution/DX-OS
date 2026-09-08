package HaUI.ImplSolution.DX_Farm.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DiagnosisResponse(Long id,Long diseaseReportId,String diseaseCode,String diseaseName,BigDecimal confidence,String modelVersion,Instant diagnosedAt,String recommendationText,String approvalStatus,String approvedBy,Instant approvedAt,String rejectionReason,TreatmentRuleResponse treatmentRule){}
