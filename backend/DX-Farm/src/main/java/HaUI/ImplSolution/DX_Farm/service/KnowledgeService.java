// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.KnowledgeAnswerResponse;
import HaUI.ImplSolution.DX_Farm.dto.KnowledgeAnswerResponse.Passage;
import HaUI.ImplSolution.DX_Farm.dto.KnowledgeAnswerResponse.Source;
import HaUI.ImplSolution.DX_Farm.dto.KnowledgeAskRequest;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Question answering over the RAG corpus: passages come from the AI retriever and carry the
// documents they are based on. Nothing is generated.
@Service
public class KnowledgeService {

  private static final String SOURCES_RESOURCE = "knowledge-sources.json";
  private static final int TOP_K = 4;

  private final AiDiagnosisClient aiClient;
  private final ObjectMapper objectMapper;
  private final double minScore;
  private final Map<String, Source> sourcesById = new HashMap<>();

  public KnowledgeService(AiDiagnosisClient aiClient, ObjectMapper objectMapper,
      @Value("${knowledge.min-score}") double minScore) {
    this.aiClient = aiClient;
    this.objectMapper = objectMapper;
    this.minScore = minScore;
  }

  @PostConstruct
  void loadSources() throws IOException {
    try (InputStream in = new ClassPathResource(SOURCES_RESOURCE).getInputStream()) {
      for (JsonNode node : objectMapper.readTree(in)) {
        Source source = new Source(node.path("sourceId").asText(), node.path("title").asText(),
            node.path("organization").asText(null), node.path("url").asText(null),
            node.path("license").asText(null), node.path("reviewStatus").asText(null));
        sourcesById.put(source.sourceId(), source);
      }
    }
  }

  public KnowledgeAnswerResponse ask(KnowledgeAskRequest request) {
    String diseaseCode = request.diseaseCode() == null || request.diseaseCode().isBlank() ? null
        : request.diseaseCode().trim();
    List<Passage> passages =
        aiClient.retrieveKnowledge(request.question().trim(), diseaseCode, TOP_K).stream()
            .filter(chunk -> chunk.score() >= minScore).map(this::toPassage).toList();
    return new KnowledgeAnswerResponse(request.question().trim(), passages);
  }

  private Passage toPassage(AiDiagnosisClient.KnowledgeChunk chunk) {
    // An id missing from the mapping is still shown, by its id, rather than dropped.
    List<Source> sources = chunk.sourceIds().stream()
        .map(id -> sourcesById.getOrDefault(id, new Source(id, id, null, null, null, null)))
        .toList();
    return new Passage(chunk.content(), chunk.documentTitle(), chunk.diseaseCode(), chunk.score(),
        sources);
  }
}
