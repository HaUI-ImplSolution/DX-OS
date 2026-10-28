// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.ai;

import HaUI.ImplSolution.dxos.core.api.exception.AiRequestRejectedException;
import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeAnswer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClientResponseException;

/** Shared contract and failure handling for optional grounded knowledge answers. */
public final class KnowledgeAnswerGenerator {

  private KnowledgeAnswerGenerator() {}

  public static GeneratedAnswer generate(AiGateway aiGateway, KnowledgeAnswer evidence,
      String namespace, String promptVersion, Map<String, Object> extraFields) {
    if (evidence.passages().isEmpty()) {
      return GeneratedAnswer.fallback(false,
          "RERANK_UNAVAILABLE".equals(evidence.reasonCode()) ? "generation_failed"
              : "insufficient_evidence");
    }
    Set<String> sources = evidence.passages().stream().flatMap(p -> p.sources().stream())
        .map(source -> source.sourceId()).collect(Collectors.toSet());
    List<Map<String, Object>> passages =
        evidence.passages().stream()
            .map(p -> Map.<String, Object>of("content", p.content(), "document_title",
                p.documentTitle(), "source_ids",
                p.sources().stream().map(source -> source.sourceId()).toList()))
            .toList();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("question", evidence.question());
    body.put("namespace", namespace);
    body.put("passages", passages);
    body.putAll(extraFields);
    try {
      GeneratedAnswer result =
          aiGateway.postJson("/api/v1/knowledge/answer", body, GeneratedAnswer.class);
      return result != null && result.supportedBy(sources)
          && (!result.mode().equals("generated") || promptVersion.equals(result.promptVersion()))
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

  public static GeneratedAnswer generate(AiGateway aiGateway, KnowledgeAnswer evidence,
      String namespace, String promptVersion) {
    return generate(aiGateway, evidence, namespace, promptVersion, Map.of());
  }
}
