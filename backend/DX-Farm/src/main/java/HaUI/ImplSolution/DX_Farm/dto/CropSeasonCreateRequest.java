package HaUI.ImplSolution.DX_Farm.dto;

import HaUI.ImplSolution.DX_Farm.model.SeasonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// @formatter:off
public record CropSeasonCreateRequest(
  // Rice variety code from the catalogue (GET /api/v1/rice-varieties)
  @NotBlank
  String riceVarietyCode,

  // Ngày bắt đầu vụ
  @NotNull
  LocalDate startDate,

  // Season type; defaults to the one suggested by the start month
  SeasonType seasonType,

  // Expected harvest date; defaults to the date suggested for the variety
  LocalDate expectedHarvestDate,

  // Required when the expected date is outside the growth range of the variety
  @Size(max = 255)
  String reasonForOverride
){}
