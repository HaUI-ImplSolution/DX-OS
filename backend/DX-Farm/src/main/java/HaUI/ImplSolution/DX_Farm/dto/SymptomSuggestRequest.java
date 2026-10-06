package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// @formatter:off
public record SymptomSuggestRequest(
  @NotBlank @Size(max = 4000) String symptomText
) {}
