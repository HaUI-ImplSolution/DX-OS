// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.ai.GeneratedAnswer;
import HaUI.ImplSolution.dxos.core.api.ai.KnowledgeAnswerGenerator;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeAnswer;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeGateway;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.AnswerResponse;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.AskRequest;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.Passage;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.Source;
import java.util.Map;
import org.springframework.stereotype.Service;

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
    return KnowledgeAnswerGenerator.generate(aiGateway, evidence, "lab", "lab_qa-v2");
  }
}
