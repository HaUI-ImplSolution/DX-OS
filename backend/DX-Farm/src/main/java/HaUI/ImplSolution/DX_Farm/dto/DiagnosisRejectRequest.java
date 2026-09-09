package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;

// @formatter:off
public record DiagnosisRejectRequest(
  @NotBlank
  String rejectedBy,

  @NotBlank
  String reason
){}
