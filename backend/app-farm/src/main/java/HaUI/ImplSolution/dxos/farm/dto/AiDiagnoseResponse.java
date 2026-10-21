// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

// Response AI -> Backend cho POST /api/v1/diagnose.
// @formatter:off
public record AiDiagnoseResponse(
  @JsonProperty("request_id") String requestId,
  @JsonProperty("cv_result") CvResult cvResult,
  @JsonProperty("rag_evidence") List<RagEvidence> ragEvidence,
  @JsonProperty("structured_context") StructuredContext structuredContext,
  Recommendation recommendation
){

  public record CvResult(
    @JsonProperty("disease_code") String diseaseCode,
    @JsonProperty("disease_name") String diseaseName,
    BigDecimal confidence,
    @JsonProperty("model_name") String modelName,
    @JsonProperty("model_version") String modelVersion
  ){}

  public record RagEvidence(
    String content,
    @JsonProperty("document_title") String documentTitle,
    @JsonProperty("chunk_index") Integer chunkIndex,
    @JsonProperty("source_ids") List<String> sourceIds,
    Double similarity
  ){}

  public record StructuredContext(
    String path,
    List<RuleFact> rules
  ){}

  public record RuleFact(
    @JsonProperty("rule_id") String ruleId,
    String pesticide,
    @JsonProperty("dose_min") BigDecimal doseMin,
    @JsonProperty("dose_max") BigDecimal doseMax,
    @JsonProperty("dose_unit") String doseUnit,
    @JsonProperty("phi_days") Integer phiDays,
    String version
  ){}

  public record Recommendation(
    @JsonProperty("selected_rule_id") String selectedRuleId,
    @JsonProperty("requires_engineer_approval") Boolean requiresEngineerApproval
  ){}
}
