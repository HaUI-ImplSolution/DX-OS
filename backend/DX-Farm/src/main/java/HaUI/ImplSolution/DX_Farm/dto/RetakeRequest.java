package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// @formatter:off
public record RetakeRequest(
  @NotBlank @Size(max = 1000) String reason
) {}
