package HaUI.ImplSolution.DX_Farm.dto;

import java.time.Instant;
import java.util.List;

// @formatter:off
public record DiseaseReportResponse(
  Long id,
  Long cropSeasonId,
  String imageUrl,
  String reportedBy,
  Instant reportedAt,
  DiagnosisResponse diagnosis,
  String status,
  String retakeReason,
  Long retakeOf,
  List<Long> retakeIds,
  ImagePrecheckResponse quality
) {}
