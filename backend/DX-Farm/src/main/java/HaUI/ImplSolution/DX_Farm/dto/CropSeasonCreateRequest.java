package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// @formatter:off
public record CropSeasonCreateRequest(
  // Giống lúa gieo trồng
  @NotBlank
  String riceVariety,

  // Ngày bắt đầu vụ
  @NotNull
  LocalDate startDate,

  // Ngày dự kiến thu hoạch
  LocalDate expectedHarvestDate
){}
