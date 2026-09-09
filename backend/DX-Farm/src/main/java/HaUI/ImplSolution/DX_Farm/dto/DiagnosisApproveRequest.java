package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;

// @formatter:off
public record DiagnosisApproveRequest(
  @NotBlank
  String approvedBy
){}
