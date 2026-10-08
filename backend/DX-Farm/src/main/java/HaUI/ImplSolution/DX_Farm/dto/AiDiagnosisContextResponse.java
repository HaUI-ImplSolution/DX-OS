// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Response Backend -> AI cho GET /internal/v1/diagnosis-context.
// @formatter:off
public record AiDiagnosisContextResponse(
  @JsonProperty("request_id") String requestId,
  @JsonProperty("disease_code") String diseaseCode,
  @JsonProperty("disease_name") String diseaseName,
  List<RuleFact> rules,
  @JsonProperty("recent_treatments") List<RecentTreatment> recentTreatments
){

  public record RuleFact(
    @JsonProperty("rule_id") String ruleId,
    String pesticide,
    @JsonProperty("dose_min") BigDecimal doseMin,
    @JsonProperty("dose_max") BigDecimal doseMax,
    @JsonProperty("dose_unit") String doseUnit,
    @JsonProperty("phi_days") Integer phiDays,
    String version
  ){}

  public record RecentTreatment(
    @JsonProperty("pesticide_name") String pesticideName,
    @JsonProperty("applied_at") LocalDate appliedAt,
    @JsonProperty("harvest_locked_until") LocalDate harvestLockedUntil
  ){}
}
