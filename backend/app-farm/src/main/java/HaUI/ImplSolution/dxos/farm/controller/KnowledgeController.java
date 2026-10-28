// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.controller;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAnswerResponse;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAskRequest;
import HaUI.ImplSolution.dxos.farm.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "Knowledge", description = "Rice disease knowledge base - Intelligence")
public class KnowledgeController {

  private final KnowledgeService knowledgeService;

  public KnowledgeController(KnowledgeService knowledgeService) {
    this.knowledgeService = knowledgeService;
  }

  @PostMapping("/ask")
  @Operation(summary = "Ask a question of the rice knowledge base",
      description = "Any signed-in user. Returns the best matching passages of the corpus, each"
          + " with the documents it is based on (title, organization, link, license, review"
          + " status). Passages remain verbatim evidence. includeAnswer defaults to false; when"
          + " true, adds a structured Qwen answer with explicit generation/fallback mode and an optional reason. Failed generation returns no answer text. No"
          + " medication dose or PHI is generated. 503 when retrieval is unavailable.")
  public ApiResponse<KnowledgeAnswerResponse> ask(@Valid @RequestBody KnowledgeAskRequest request,
      @RequestParam(defaultValue = "false") boolean includeAnswer) {
    return ApiResponse.success(knowledgeService.ask(request, includeAnswer));
  }
}
