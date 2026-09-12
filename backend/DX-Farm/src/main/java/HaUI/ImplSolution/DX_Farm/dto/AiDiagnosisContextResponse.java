package HaUI.ImplSolution.DX_Farm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Response Backend -> AI cho GET /internal/v1/diagnosis-context.
// Cùng hình dạng rule_id/pesticide/dose_amount/... với StructuredContext.RuleFact mà AI
// echo lại trong AiDiagnoseResponse - đây là nguồn Backend tự tạo ra nó.
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
    @JsonProperty("dose_amount") BigDecimal doseAmount,
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
