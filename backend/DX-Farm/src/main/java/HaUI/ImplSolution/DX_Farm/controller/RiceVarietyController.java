// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.RiceVarietyResponse;
import HaUI.ImplSolution.DX_Farm.service.RiceVarietyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rice-varieties")
@Tag(name = "RiceVariety", description = "Rice variety catalogue - Operational Data")
public class RiceVarietyController {

  private final RiceVarietyService riceVarietyService;

  public RiceVarietyController(RiceVarietyService riceVarietyService) {
    this.riceVarietyService = riceVarietyService;
  }

  @GetMapping
  @Operation(summary = "List the rice varieties a crop season can use",
      description = "Each variety carries its growth range in days and the source of the figures;"
          + " reviewStatus IN_REVIEW means no agronomist has confirmed them yet.")
  public ApiResponse<List<RiceVarietyResponse>> list() {
    return ApiResponse.success(riceVarietyService.listActive());
  }
}
