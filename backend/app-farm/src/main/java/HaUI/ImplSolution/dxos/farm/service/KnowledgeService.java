// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.ai.GeneratedAnswer;
import HaUI.ImplSolution.dxos.core.api.ai.KnowledgeAnswerGenerator;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeAnswer;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeGateway;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgePassage;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeSource;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAnswerResponse;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAnswerResponse.Passage;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAnswerResponse.Source;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAskRequest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

// Answers over the rice knowledge corpus, optionally limited to one disease.
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
    if (diseaseCode == null || diseaseCode.isBlank()) {
      return KnowledgeAnswerGenerator.generate(aiGateway, evidence, "farm", "farm_qa-v2");
    }
    return KnowledgeAnswerGenerator.generate(aiGateway, evidence, "farm", "farm_qa-v2",
        Map.of("disease_code", diseaseCode.trim()));
  }
}
