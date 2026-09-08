package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;

public record DiagnosisApproveRequest(@NotBlank String approvedBy){}
