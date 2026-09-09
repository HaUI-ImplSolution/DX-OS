package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;

// @formatter:off
public record DiagnosisApproveRequest(
  // Tên kỹ sư thực hiện duyệt
  @NotBlank
  String approvedBy
){}
