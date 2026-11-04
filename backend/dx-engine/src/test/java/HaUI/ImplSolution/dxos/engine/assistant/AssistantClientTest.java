// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AssistantClientTest {
  @Test
  void sendsTypedDocumentsAndServiceKeyAsJson() throws Exception {
    AtomicReference<String> body = new AtomicReference<>();
    AtomicReference<String> key = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/assistant/rank", request -> {
      body.set(new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      key.set(request.getRequestHeaders().getFirst("X-Service-Api-Key"));
      byte[] response = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
      request.getResponseHeaders().set("Content-Type", "application/json");
      request.sendResponseHeaders(200, response.length);
      request.getResponseBody().write(response);
      request.close();
    });
    server.start();
    try {
      AssistantClient client = new AssistantClient(new AssistantSettings(true, false,
          "http://127.0.0.1:" + server.getAddress().getPort(), "test-key"));
      var result = client.call("rank",
          Map.of("query", "Máy in", "documents",
              List.of(new AssistantContract.TextDocument("record:1", "Máy in offline")),
              "timeoutSeconds", 30d),
          AssistantContract.RankResult.class, System.nanoTime() + Duration.ofSeconds(5).toNanos());
      assertNotNull(result);
      assertEquals("test-key", key.get());
      assertTrue(body.get().contains("\"text\":\"Máy in offline\""), body.get());
      assertTrue(body.get().contains("\"id\":\"record:1\""), body.get());
    } finally {
      server.stop(0);
    }
  }
}
