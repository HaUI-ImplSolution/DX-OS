package HaUI.ImplSolution.DX_Farm.dto;

import java.time.Instant;

public record DiseaseReportResponse(Long id,Long cropSeasonId,String imageRef,String reportedBy,Instant reportedAt,DiagnosisResponse diagnosis){}
