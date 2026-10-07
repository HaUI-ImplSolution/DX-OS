package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.KnowledgeAnswerResponse;
import HaUI.ImplSolution.DX_Farm.dto.KnowledgeAskRequest;
import HaUI.ImplSolution.DX_Farm.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
  @Operation(summary = "Ask a question of the disease knowledge base",
      description = "Any signed-in user. Returns the best matching passages of the corpus, each"
          + " with the documents it is based on (title, organization, link, license, review"
          + " status). Passages are quoted, never generated; the list is empty when nothing is"
          + " relevant enough. 503 when the AI service is unavailable.")
  public ApiResponse<KnowledgeAnswerResponse> ask(@Valid @RequestBody KnowledgeAskRequest request) {
    return ApiResponse.success(knowledgeService.ask(request));
  }
}
