// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.controller;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.AnswerResponse;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.AskRequest;
import HaUI.ImplSolution.dxos.lab.service.KnowledgeService;
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
@Tag(name = "Knowledge", description = "Lab regulations and guides - Intelligence")
public class KnowledgeController {

  private final KnowledgeService knowledgeService;

  public KnowledgeController(KnowledgeService knowledgeService) {
    this.knowledgeService = knowledgeService;
  }

  @PostMapping("/ask")
  @Operation(summary = "Ask a question of the lab knowledge base",
      description = "Any signed-in user. Returns the best matching passages of the corpus, each"
          + " with the documents it is based on (title, organization, publisher page, original"
          + " file, license, review status). Passages remain verbatim evidence. includeAnswer"
          + " defaults to false; when true, adds a structured Qwen answer with explicit"
          + " generation/fallback mode and an optional reason; failed generation returns no answer text. 503 when retrieval is unavailable.")
  public ApiResponse<AnswerResponse> ask(@Valid @RequestBody AskRequest request,
      @RequestParam(defaultValue = "false") boolean includeAnswer) {
    return ApiResponse.success(knowledgeService.ask(request, includeAnswer));
  }
}
