// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.controller;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.farm.dto.DiseaseResponse;
import HaUI.ImplSolution.dxos.farm.service.DiseaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diseases")
@Tag(name = "Disease", description = "Structured Knowledge - danh mục bệnh")
public class DiseaseController {

  private final DiseaseService diseaseService;

  public DiseaseController(DiseaseService diseaseService) {
    this.diseaseService = diseaseService;
  }

  @GetMapping
  @Operation(summary = "List all diseases")
  public ApiResponse<List<DiseaseResponse>> listAll() {
    return ApiResponse.success(diseaseService.listAll());
  }
}
