// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeAnswer;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeGateway;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.AnswerResponse;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.AskRequest;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.Passage;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.Source;
import java.util.Map;
import org.springframework.stereotype.Service;

// Question answering over the lab regulations and guides: passages are quoted, never generated.
@Service
public class KnowledgeService {

  private static final int TOP_K = 4;

  private final KnowledgeGateway knowledgeGateway;

  public KnowledgeService(KnowledgeGateway knowledgeGateway) {
    this.knowledgeGateway = knowledgeGateway;
  }

  public AnswerResponse ask(AskRequest request) {
    KnowledgeAnswer answer = knowledgeGateway.ask(LabKnowledgeNamespace.NAMESPACE,
        request.question().strip(), Map.of(), TOP_K);
    return new AnswerResponse(answer.question(),
        answer.passages().stream()
            .map(passage -> new Passage(passage.content(), passage.documentTitle(), passage.score(),
                passage.sources().stream().map(Source::of).toList()))
            .toList());
  }
}
