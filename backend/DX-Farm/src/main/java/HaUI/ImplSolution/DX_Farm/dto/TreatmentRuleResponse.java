// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import java.math.BigDecimal;

// @formatter:off
public record TreatmentRuleResponse(
  Long id,
  String diseaseCode,
  String pesticideCode,
  BigDecimal doseMin,
  BigDecimal doseMax,
  String doseUnit,
  Integer phiDays,
  String notes,
  String source,
  String version
){}
