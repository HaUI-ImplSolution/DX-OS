// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.knowledge;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeAnswer;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeGateway;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeNamespace;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgePassage;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

// Question answering over the RAG corpus: passages come from the AI retriever and carry the
// documents they are based on. Nothing is generated.
@Service
public class KnowledgeGatewayService implements KnowledgeGateway {

  private static final String RETRIEVE_PATH = "/api/v1/disease/knowledge/retrieve";
  private static final Set<String> PASSAGE_FIELDS =
      Set.of("content", "document_title", "score", "source_ids");

  private final AiGateway aiGateway;
  private final double minScore;
  private final Map<String, KnowledgeNamespace> namespaces = new HashMap<>();

  public KnowledgeGatewayService(AiGateway aiGateway,
      ObjectProvider<KnowledgeNamespace> namespaceProviders,
      @Value("${knowledge.min-score:0.40}") double minScore) {
    this.aiGateway = aiGateway;
    this.minScore = minScore;
    namespaceProviders.orderedStream()
        .forEach(namespace -> namespaces.put(namespace.namespace(), namespace));
  }

  @Override
  public KnowledgeAnswer ask(String namespace, String question, Map<String, String> filters,
      int topK) {
    KnowledgeNamespace corpus = namespaces.get(namespace);
    if (corpus == null) {
      throw new IllegalArgumentException("Knowledge namespace is not registered: " + namespace);
    }
    ScopeDecision scope = classify(namespace, question);
    if (!scope.retrievalAllowed()) {
      return new KnowledgeAnswer(question, List.of(), "OUT_OF_SCOPE", scope.reasonCode(),
          namespace);
    }
    Map<String, Object> body = new LinkedHashMap<>(filters);
    body.put("query", question);
    body.put("top_k", topK);
    body.put("namespace", namespace);
    List<Map<String, Object>> chunks = aiGateway.postJson(RETRIEVE_PATH, body,
        new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    List<Map<String, Object>> relevant = chunks.stream()
        .filter(chunk -> ((Number) chunk.getOrDefault("score", 0)).doubleValue() >= minScore)
        .toList();
    List<KnowledgePassage> passages = mergeByDocument(relevant, corpus.sources());
    return new KnowledgeAnswer(question, passages,
        passages.isEmpty() ? "IN_SCOPE_NO_EVIDENCE" : "ANSWERABLE",
        passages.isEmpty() ? "NO_EVIDENCE" : "EVIDENCE_FOUND", namespace);
  }

  private ScopeDecision classify(String namespace, String question) {
    String text = fold(question);
    if ("farm".equals(namespace)) {
      if (containsAny(text, "o to", "xe hoi", "chung khoan", "bong da", "du lich")) {
        return new ScopeDecision(false, "EXPLICIT_UNRELATED_TOPIC");
      }
      return new ScopeDecision(containsAny(text, "lua", "ruong", "nong nghiep", "hat giong",
          "phan bon", "tuoi", "benh", "co dai", "thu hoach", "dat"), "NO_FARM_DOMAIN_TERM");
    }
    if ("lab".equals(namespace)) {
      if (containsAny(text, "o to", "xe hoi", "nau an")) {
        return new ScopeDecision(false, "EXPLICIT_UNRELATED_TOPIC");
      }
      return new ScopeDecision(containsAny(text, "haui", "wifi", "email", "mat khau", "tai khoan",
          "tin hoc", "cntt", "phan mem", "thiet bi", "quy dinh", "dich vu", "sict", "faronics",
          "so tay", "an toan thong tin", "mang khong day"), "NO_LAB_DOMAIN_TERM");
    }
    return new ScopeDecision(false, "UNKNOWN_NAMESPACE");
  }

  private static String fold(String value) {
    return Normalizer.normalize(value.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
  }

  private static boolean containsAny(String text, String... terms) {
    for (String term : terms) {
      if (text
          .matches(".*(?<![\\p{L}])" + java.util.regex.Pattern.quote(term) + "(?![\\p{L}]).*")) {
        return true;
      }
    }
    return false;
  }

  private record ScopeDecision(boolean retrievalAllowed, String reasonCode) {}

  // The chunks of one document become one passage: its lines in document order without repeats,
  // the best score and the union of its sources. Documents keep the order of their best chunk.
  private List<KnowledgePassage> mergeByDocument(List<Map<String, Object>> chunks,
      Map<String, KnowledgeSource> sources) {
    Map<Object, List<Map<String, Object>>> byDocument = new LinkedHashMap<>();
    for (Map<String, Object> chunk : chunks) {
      Object key = chunk.getOrDefault("document_id", chunk.get("content"));
      byDocument.computeIfAbsent(key, k -> new ArrayList<>()).add(chunk);
    }
    return byDocument.values().stream().map(group -> {
      KnowledgePassage best = toPassage(group.get(0), sources);
      Set<String> lines = new LinkedHashSet<>();
      Map<String, KnowledgeSource> cited = new LinkedHashMap<>();
      group.stream()
          .sorted(Comparator.comparing(
              chunk -> chunk.get("chunk_index") instanceof Number index ? index.intValue()
                  : Integer.MAX_VALUE))
          .forEach(chunk -> ((String) chunk.get("content")).lines().map(String::strip)
              .filter(line -> !line.isEmpty()).forEach(lines::add));
      group.forEach(chunk -> toPassage(chunk, sources).sources()
          .forEach(source -> cited.putIfAbsent(source.sourceId(), source)));
      return new KnowledgePassage(String.join("\n", lines), best.documentTitle(), best.score(),
          best.attributes(), List.copyOf(cited.values()));
    }).toList();
  }

  private KnowledgePassage toPassage(Map<String, Object> chunk,
      Map<String, KnowledgeSource> sources) {
    // A source id missing from the catalogue is still shown, by its id, rather than dropped.
    List<String> sourceIds =
        chunk.get("source_ids") instanceof List<?> ids ? ids.stream().map(String::valueOf).toList()
            : List.of();
    List<KnowledgeSource> resolved = sourceIds.stream().map(
        id -> sources.getOrDefault(id, new KnowledgeSource(id, id, null, null, null, null, null)))
        .toList();
    Map<String, String> attributes = new LinkedHashMap<>();
    chunk.forEach((key, value) -> {
      if (value instanceof String text && !PASSAGE_FIELDS.contains(key)) {
        attributes.put(key, text);
      }
    });
    return new KnowledgePassage((String) chunk.get("content"), (String) chunk.get("document_title"),
        ((Number) chunk.getOrDefault("score", 0)).doubleValue(), attributes, resolved);
  }
}
