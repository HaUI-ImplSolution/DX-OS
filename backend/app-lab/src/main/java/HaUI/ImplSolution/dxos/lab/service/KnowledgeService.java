// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.exception.AiRequestRejectedException;
import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeAnswer;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeGateway;
import HaUI.ImplSolution.dxos.lab.dto.GeneratedAnswer;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.AnswerResponse;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.AskRequest;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.Passage;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.Source;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

// Answers and verbatim evidence from the Lab knowledge base.
@Service
public class KnowledgeService {

  private static final int TOP_K = 4;

  private final KnowledgeGateway knowledgeGateway;
  private final AiGateway aiGateway;

  public KnowledgeService(KnowledgeGateway knowledgeGateway, AiGateway aiGateway) {
    this.knowledgeGateway = knowledgeGateway;
    this.aiGateway = aiGateway;
  }

  public AnswerResponse ask(AskRequest request) {
    return ask(request, false);
  }

  public AnswerResponse ask(AskRequest request, boolean includeAnswer) {
    KnowledgeAnswer answer = knowledgeGateway.ask(LabKnowledgeNamespace.NAMESPACE,
        request.question().strip(), Map.of(), TOP_K);
    return new AnswerResponse(answer.question(), answer.scopeStatus(), answer.reasonCode(),
        answer.namespace(),
        answer.passages().stream()
            .map(passage -> new Passage(passage.content(), passage.documentTitle(), passage.score(),
                passage.attributes().get("document_version"),
                passage.sources().stream().map(Source::of).toList()))
            .toList(),
        includeAnswer ? generateAnswer(answer) : null);
  }

  private GeneratedAnswer generateAnswer(KnowledgeAnswer evidence) {
    if (evidence.passages().isEmpty()) {
      return GeneratedAnswer.fallback(false,
          "RERANK_UNAVAILABLE".equals(evidence.reasonCode()) ? "generation_failed"
              : "insufficient_evidence");
    }
    Set<String> sources = evidence.passages().stream().flatMap(p -> p.sources().stream())
        .map(source -> source.sourceId()).collect(Collectors.toSet());
    List<Map<String, Object>> passages = evidence.passages().stream().map(p -> {
      Map<String, Object> item = Map.of("content", p.content(), "document_title", p.documentTitle(),
          "source_ids", p.sources().stream().map(source -> source.sourceId()).toList());
      return item;
    }).toList();
    try {
      GeneratedAnswer result = aiGateway.postJson("/api/v1/knowledge/answer",
          Map.of("question", evidence.question(), "namespace", "lab", "passages", passages),
          GeneratedAnswer.class);
      return result != null && result.supportedBy(sources)
          && (!result.mode().equals("generated") || "lab_qa-v2".equals(result.promptVersion()))
              ? result
              : GeneratedAnswer.fallback(true, "conflicting_output");
    } catch (AiRequestRejectedException e) {
      if (e.status() != 404) {
        throw e;
      }
      return GeneratedAnswer.fallback(true);
    } catch (AiServiceException e) {
      if (e.getCause() instanceof RestClientResponseException response
          && (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403)) {
        throw e;
      }
      return GeneratedAnswer.fallback(true);
    }
  }
}
