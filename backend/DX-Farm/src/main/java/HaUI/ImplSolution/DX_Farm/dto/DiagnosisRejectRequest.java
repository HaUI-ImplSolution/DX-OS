// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;

// @formatter:off
public record DiagnosisRejectRequest(
  // Lý do từ chối (rejectedBy lấy từ kỹ sư đang đăng nhập, không nhận qua request)
  @NotBlank
  String reason
){}
