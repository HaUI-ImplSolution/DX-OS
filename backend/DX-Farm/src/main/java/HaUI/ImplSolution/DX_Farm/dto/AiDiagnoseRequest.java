// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Request Backend -> AI cho POST /api/v1/diagnose
// @formatter:off
public record AiDiagnoseRequest(
  // Trace xuyên suốt Backend <-> AI cho một lượt chẩn đoán
  @JsonProperty("request_id") String requestId,

  // Mùa vụ mà disease report thuộc về
  @JsonProperty("crop_season_id") Long cropSeasonId,

  // URL ảnh lá bệnh đã upload lên Backend
  @JsonProperty("image_url") String imageUrl,

  // Ngữ cảnh thực địa: thửa, mùa vụ, lịch sử phun gần nhất
  @JsonProperty("field_context") FieldContext fieldContext
){

  public record FieldContext(
    @JsonProperty("parcel_code") String parcelCode,
    @JsonProperty("crop_name") String cropName,
    @JsonProperty("growth_stage") String growthStage,
    @JsonProperty("recent_treatments") List<RecentTreatment> recentTreatments
  ){}

  public record RecentTreatment(
    @JsonProperty("pesticide_name") String pesticideName,
    @JsonProperty("applied_at") Instant appliedAt,
    @JsonProperty("harvest_locked_until") LocalDate harvestLockedUntil
  ){}
}
