package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;

public record DiagnosisRejectRequest(@NotBlank String rejectedBy,@NotBlank String reason){}
