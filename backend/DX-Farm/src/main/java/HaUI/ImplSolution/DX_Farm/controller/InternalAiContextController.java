// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.AiDiagnosisContextResponse;
import HaUI.ImplSolution.DX_Farm.service.InternalAiContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Internal API read-only cho AI Service - chỉ AI được gọi (X-Service-Api-Key)
// Không expose ở đây bất kỳ endpoint ghi nào.
@RestController
@RequestMapping("/internal/v1")
@Tag(name = "InternalAiContext",
    description = "Structured facts read-only cho AI Service (disease_code -> treatment rule + lịch sử phun)")
public class InternalAiContextController {

  private static final Logger log = LoggerFactory.getLogger(InternalAiContextController.class);

  private final InternalAiContextService internalAiContextService;

  public InternalAiContextController(InternalAiContextService internalAiContextService) {
    this.internalAiContextService = internalAiContextService;
  }

  @GetMapping("/diagnosis-context")
  @SecurityRequirement(name = "serviceApiKey")
  @Operation(
      summary = "AI Service lấy treatment rule + lịch sử phun sau khi CV xác định disease_code",
      description = "Gọi bằng service API key nội bộ (header X-Service-Api-Key), không dùng JWT người dùng.")
  public AiDiagnosisContextResponse getDiagnosisContext(
      @Parameter(description = "Trace id xuyên suốt lượt gọi Backend<->AI")
      @RequestParam("request_id") String requestId,
      @Parameter(description = "Mã bệnh canonical do CV trả về, khớp Disease.code")
      @RequestParam("disease_code") String diseaseCode,
      @Parameter(description = "Vụ mùa của disease report đang được AI chẩn đoán")
      @RequestParam("crop_season_id") Long cropSeasonId) {
    log.info("Internal AI context request_id={} disease_code={} crop_season_id={}", requestId,
        diseaseCode, cropSeasonId);
    return internalAiContextService.getDiagnosisContext(requestId, diseaseCode, cropSeasonId);
  }
}
