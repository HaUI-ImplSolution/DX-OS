package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.TreatmentRuleResponse;
import HaUI.ImplSolution.DX_Farm.service.TreatmentRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/treatment-rules")
@Tag(name = "TreatmentRule", description = "Structured Knowledge - phác đồ Disease-Pesticide")
public class TreatmentRuleController {

  private final TreatmentRuleService treatmentRuleService;

  public TreatmentRuleController(TreatmentRuleService treatmentRuleService) {
    this.treatmentRuleService = treatmentRuleService;
  }

  @GetMapping
  @Operation(summary = "List all treatment rules")
  public ApiResponse<List<TreatmentRuleResponse>> listAll() {
    return ApiResponse.success(treatmentRuleService.listAll());
  }
}
