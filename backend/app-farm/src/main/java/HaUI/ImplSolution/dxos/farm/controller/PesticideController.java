// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.controller;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.farm.dto.PesticideResponse;
import HaUI.ImplSolution.dxos.farm.service.PesticideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pesticides")
@Tag(name = "Pesticide", description = "Structured Knowledge - danh mục thuốc BVTV")
public class PesticideController {

  private final PesticideService pesticideService;

  public PesticideController(PesticideService pesticideService) {
    this.pesticideService = pesticideService;
  }

  @GetMapping
  @Operation(summary = "List all pesticides")
  public ApiResponse<List<PesticideResponse>> listAll() {
    return ApiResponse.success(pesticideService.listAll());
  }
}
