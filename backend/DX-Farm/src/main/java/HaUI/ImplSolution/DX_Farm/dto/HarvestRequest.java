package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// @formatter:off
public record HarvestRequest(
  // Day the harvest took place; defaults to today, never in the future
  LocalDate actualHarvestDate,

  // Harvested weight in kilograms
  @NotNull
  @DecimalMin(value = "0.0", inclusive = false)
  BigDecimal yieldKg,

  // Harvested area in hectares; defaults to the whole parcel, never above it
  @DecimalMin(value = "0.0", inclusive = false)
  BigDecimal harvestedAreaHectares,

  // Must be true when the harvest is before the expected date
  Boolean confirmEarly,

  // Required together with confirmEarly
  @Size(max = 255)
  String earlyReason,

  // Required when an administrator declares the harvest in place of the farmer
  @Size(max = 255)
  String reasonOnBehalf
){}
