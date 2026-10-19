// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeNamespace;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// The rice disease corpus and the catalogue of documents it is based on.
@Component
public class FarmKnowledgeNamespace implements KnowledgeNamespace {

  public static final String NAMESPACE = "farm";
  private static final String SOURCES_RESOURCE = "knowledge-sources.json";

  private final Map<String, KnowledgeSource> sources = new LinkedHashMap<>();

  public FarmKnowledgeNamespace(ObjectMapper objectMapper) throws IOException {
    try (InputStream in = new ClassPathResource(SOURCES_RESOURCE).getInputStream()) {
      for (JsonNode node : objectMapper.readTree(in)) {
        KnowledgeSource source = new KnowledgeSource(node.path("sourceId").asText(),
            node.path("title").asText(), node.path("organization").asText(null),
            node.path("url").asText(null), node.path("documentUrl").asText(null),
            node.path("license").asText(null), node.path("reviewStatus").asText(null));
        sources.put(source.sourceId(), source);
      }
    }
  }

  @Override
  public String namespace() {
    return NAMESPACE;
  }

  @Override
  public Map<String, KnowledgeSource> sources() {
    return sources;
  }
}
