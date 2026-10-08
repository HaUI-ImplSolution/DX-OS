// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// @formatter:off
public record DiagnosisCreateRequest(
  // Mã bệnh do AI Service (hoặc nhập tay) xác định
  @NotBlank
  String diseaseCode,

  // Độ tin cậy của chẩn đoán (0.0 - 1.0)
  @NotNull
  @DecimalMin("0.0")
  @DecimalMax("1.0")
  BigDecimal confidence,

  // Phiên bản model AI đã dùng để chẩn đoán
  @NotBlank
  String modelVersion,

  // Symptoms the engineer observed; kept with the diagnosis and copied to the training label on approval
  @Size(max = 4000) String symptomText
){
  public DiagnosisCreateRequest(String diseaseCode, BigDecimal confidence, String modelVersion) {
    this(diseaseCode, confidence, modelVersion, null);
  }
}
