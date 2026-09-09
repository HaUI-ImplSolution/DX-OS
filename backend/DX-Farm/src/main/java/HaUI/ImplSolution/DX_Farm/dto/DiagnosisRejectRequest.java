package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;

// @formatter:off
public record DiagnosisRejectRequest(
  // Tên kỹ sư thực hiện từ chối
  @NotBlank
  String rejectedBy,

  // Lý do từ chối
  @NotBlank
  String reason
){}
