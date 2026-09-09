package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// @formatter:off
public record CropSeasonCreateRequest(
  @NotBlank
  String riceVariety,

  @NotNull
  LocalDate startDate,

  LocalDate expectedHarvestDate
){}
