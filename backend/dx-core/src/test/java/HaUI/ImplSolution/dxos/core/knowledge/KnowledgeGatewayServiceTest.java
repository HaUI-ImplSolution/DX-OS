// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.knowledge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeNamespace;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;

class KnowledgeGatewayServiceTest {
  private final AiGateway ai = mock(AiGateway.class);

  @SuppressWarnings("unchecked")
  private KnowledgeGatewayService service() {
    ObjectProvider<KnowledgeNamespace> providers = mock(ObjectProvider.class);
    KnowledgeNamespace namespace = mock(KnowledgeNamespace.class);
    when(namespace.namespace()).thenReturn("registered-test");
    when(namespace.sources()).thenReturn(Map.of());
    when(providers.orderedStream()).thenAnswer(i -> Stream.of(namespace));
    return new KnowledgeGatewayService(ai, providers, 0.40);
  }

  private Map<String, Object> chunk(int index, String content, double score) {
    return Map.of("document_id", "doc", "chunk_index", index, "content", content, "score", score,
        "document_title", "Guide", "source_ids", List.of("source"));
  }

  @Test
  void anyRegisteredNamespaceUsesSearchAndKeepsHybridOrderWithoutCosineFiltering() {
    when(ai.postJson(eq("/api/v1/knowledge/search"), any(),
        eq(KnowledgeGatewayService.SearchResult.class)))
        .thenReturn(new KnowledgeGatewayService.SearchResult("registered-test", "ANSWERABLE",
            "EVIDENCE_FOUND", List.of(chunk(1, "Second", 0.1), chunk(0, "First", 0.2))));
    var result =
        service().ask("registered-test", "General question", Map.of("namespace", "spoof"), 4);
    assertThat(result.passages()).hasSize(1);
    assertThat(result.passages().getFirst().content()).isEqualTo("First\nSecond");
    verify(ai).postJson(eq("/api/v1/knowledge/search"),
        argThat((Object b) -> "registered-test".equals(((Map<?, ?>) b).get("namespace"))),
        eq(KnowledgeGatewayService.SearchResult.class));
  }

  @Test
  void refusalKeepsScopeAndDoesNotInventPassages() {
    when(ai.postJson(anyString(), any(), eq(KnowledgeGatewayService.SearchResult.class)))
        .thenReturn(new KnowledgeGatewayService.SearchResult("registered-test", "OUT_OF_SCOPE",
            "EXPLICIT_UNRELATED_TOPIC", List.of()));
    assertThat(service().ask("registered-test", "Question", Map.of(), 4).scopeStatus())
        .isEqualTo("OUT_OF_SCOPE");
  }

  @Test
  void foreignNamespaceResponseFailsClosed() {
    when(ai.postJson(anyString(), any(), eq(KnowledgeGatewayService.SearchResult.class)))
        .thenReturn(new KnowledgeGatewayService.SearchResult("other", "ANSWERABLE",
            "EVIDENCE_FOUND", List.of(chunk(0, "Text", 0.8))));
    assertThatThrownBy(() -> service().ask("registered-test", "Question", Map.of(), 4))
        .isInstanceOf(AiServiceException.class);
  }

  @Test
  void unregisteredNamespaceNeverCallsAi() {
    assertThatThrownBy(() -> service().ask("unknown", "Question", Map.of(), 4))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(ai);
  }

  @Test
  void legacyRetrieveKeepsEndpointAndCosineThreshold() {
    when(ai.postJson(eq("/api/v1/disease/knowledge/retrieve"), any(),
        any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(chunk(0, "Too weak", 0.1), chunk(1, "Supported", 0.8)));
    assertThat(service().retrieve("registered-test", "Question", Map.of(), 4).passages().getFirst()
        .content()).isEqualTo("Supported");
  }

  @Test
  void searchEnvelopeDeserializesThroughTheExistingHttpGateway() throws Exception {
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/knowledge/search", exchange -> {
      byte[] body =
          "{\"namespace\":\"registered-test\",\"scope_status\":\"IN_SCOPE_NO_EVIDENCE\",\"reason_code\":\"NO_EVIDENCE\",\"strategy\":\"hybrid_rerank\",\"passages\":[],\"rankings\":{}}"
              .getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      var transport = new HaUI.ImplSolution.dxos.core.ai.AiServiceGateway(
          "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", 1000, 3000,
          new tools.jackson.databind.ObjectMapper());
      var result = transport.postJson("/api/v1/knowledge/search", Map.of(),
          KnowledgeGatewayService.SearchResult.class);
      assertThat(result.scopeStatus()).isEqualTo("IN_SCOPE_NO_EVIDENCE");
      assertThat(result.reasonCode()).isEqualTo("NO_EVIDENCE");
      assertThat(result.namespace()).isEqualTo("registered-test");
    } finally {
      server.stop(0);
    }
  }

}
