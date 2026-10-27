// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.exception.AiRequestRejectedException;
import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeAnswer;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeGateway;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgePassage;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeSource;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAnswerResponse;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAnswerResponse.Passage;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAnswerResponse.Source;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAskRequest;
import HaUI.ImplSolution.dxos.farm.model.GeneratedAnswer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

// Question answering over the rice disease corpus, optionally limited to one disease.
@Service
public class KnowledgeService {

  private static final int TOP_K = 4;

  private final KnowledgeGateway knowledgeGateway;
  private final AiGateway aiGateway;

  public KnowledgeService(KnowledgeGateway knowledgeGateway, AiGateway aiGateway) {
    this.knowledgeGateway = knowledgeGateway;
    this.aiGateway = aiGateway;
  }

  public KnowledgeAnswerResponse ask(KnowledgeAskRequest request) {
    return ask(request, false);
  }

  public KnowledgeAnswerResponse ask(KnowledgeAskRequest request, boolean includeAnswer) {
    Map<String, String> filters =
        request.diseaseCode() == null || request.diseaseCode().isBlank() ? Map.of()
            : Map.of("disease_code", request.diseaseCode().trim());
    KnowledgeAnswer answer = knowledgeGateway.ask(FarmKnowledgeNamespace.NAMESPACE,
        request.question().trim(), filters, TOP_K);
    return new KnowledgeAnswerResponse(answer.question(), answer.scopeStatus(), answer.reasonCode(),
        answer.namespace(), answer.passages().stream().map(KnowledgeService::toPassage).toList(),
        includeAnswer ? generateAnswer(answer, request.diseaseCode()) : null);
  }

  private static Passage toPassage(KnowledgePassage passage) {
    List<Source> sources = passage.sources().stream().map(KnowledgeService::toSource).toList();
    return new Passage(passage.content(), passage.documentTitle(),
        passage.attributes().get("disease_code"), passage.score(),
        passage.attributes().get("document_version"), sources);
  }

  private static Source toSource(KnowledgeSource source) {
    return new Source(source.sourceId(), source.title(), source.organization(), source.url(),
        source.license(), source.reviewStatus());
  }

  private GeneratedAnswer generateAnswer(KnowledgeAnswer evidence, String diseaseCode) {
    if (evidence.passages().isEmpty()) {
      return GeneratedAnswer.fallback(false);
    }
    Set<String> sources = evidence.passages().stream().flatMap(p -> p.sources().stream())
        .map(source -> source.sourceId()).collect(Collectors.toSet());
    List<Map<String, Object>> passages = evidence.passages().stream().map(p -> {
      Map<String, Object> item = Map.of("content", p.content(), "document_title", p.documentTitle(),
          "source_ids", p.sources().stream().map(source -> source.sourceId()).toList());
      return item;
    }).toList();
    try {
      Map<String, Object> body = new java.util.LinkedHashMap<>(
          Map.of("question", evidence.question(), "namespace", "farm", "passages", passages));
      if (diseaseCode != null && !diseaseCode.isBlank()) {
        body.put("disease_code", diseaseCode.trim());
      }
      GeneratedAnswer result =
          aiGateway.postJson("/api/v1/knowledge/answer", body, GeneratedAnswer.class);
      return result != null && result.supportedBy(sources)
          && (!result.mode().equals("generated") || "farm_qa-v2".equals(result.promptVersion()))
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
