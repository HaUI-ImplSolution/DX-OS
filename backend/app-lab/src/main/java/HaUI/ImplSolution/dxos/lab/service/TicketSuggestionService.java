// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeAnswer;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeGateway;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.RelatedPassage;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketSuggestRequest;
import HaUI.ImplSolution.dxos.lab.dto.TicketDtos.TicketSuggestionResponse;
import HaUI.ImplSolution.dxos.lab.service.TicketClassifier.Suggestion;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// What the user sees before sending a ticket: the category and priority the rules suggest, why,
// and up to three knowledge passages that may solve the problem without a ticket.
@Service
public class TicketSuggestionService {

  private static final Logger log = LoggerFactory.getLogger(TicketSuggestionService.class);
  private static final int RELATED_PASSAGES = 3;

  private final TicketClassifier classifier;
  private final KnowledgeGateway knowledgeGateway;

  public TicketSuggestionService(TicketClassifier classifier, KnowledgeGateway knowledgeGateway) {
    this.classifier = classifier;
    this.knowledgeGateway = knowledgeGateway;
  }

  public TicketSuggestionResponse suggest(TicketSuggestRequest request) {
    String text = request.title() == null || request.title().isBlank() ? request.description()
        : request.title() + ". " + request.description();
    Suggestion suggestion = classifier.classify(text);
    List<RelatedPassage> related = List.of();
    boolean available = true;
    try {
      KnowledgeAnswer answer = knowledgeGateway.retrieve(LabKnowledgeNamespace.NAMESPACE, text,
          Map.of(), RELATED_PASSAGES);
      related = answer.passages().stream().limit(RELATED_PASSAGES).map(RelatedPassage::of).toList();
    } catch (AiServiceException e) {
      // The suggestion by rules still works without the knowledge base.
      log.warn("Knowledge base unavailable for suggestion: {}", e.getMessage());
      available = false;
    }
    if (suggestion == null) {
      return new TicketSuggestionResponse(null, null, null, List.of(),
          List.of("Chưa có loại sự cố nào đang hoạt động"), available, related);
    }
    return new TicketSuggestionResponse(suggestion.category().getCode(),
        suggestion.category().getName(), suggestion.priority(), suggestion.matchedKeywords(),
        suggestion.reasons(), available, related);
  }
}
