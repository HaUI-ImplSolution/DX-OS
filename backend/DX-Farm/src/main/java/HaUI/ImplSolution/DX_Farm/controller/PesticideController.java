package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.PesticideResponse;
import HaUI.ImplSolution.DX_Farm.service.PesticideService;
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
