// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import HaUI.ImplSolution.dxos.core.api.exception.AiRequestRejectedException;
import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.ObjectMapper;

class AiServiceGatewayTest {

  private static AiServiceGateway gateway(HttpServer server) {
    return new AiServiceGateway("http://127.0.0.1:" + server.getAddress().getPort(), "test-key",
        1000, 3000, 30, new ObjectMapper());
  }

  @Test
  void forwardsMultipartWithTheServiceKey() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> contentType = new AtomicReference<>();
    AtomicReference<String> apiKey = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    server.createContext("/upload", exchange -> {
      contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
      apiKey.set(exchange.getRequestHeaders().getFirst("X-Service-Api-Key"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
      byte[] response = "{\"passed\":true}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      var parts = new LinkedMultiValueMap<String, Object>();
      parts.add("image", new ByteArrayResource(new byte[] {(byte) 0x89, 'P', 'N', 'G'}) {
        @Override
        public String getFilename() {
          return "image.png";
        }
      });

      Map<?, ?> response = gateway(server).postMultipart("/upload", parts, Map.class);

      assertThat(response.get("passed")).isEqualTo(true);
      assertThat(contentType.get()).startsWith("multipart/form-data");
      assertThat(apiKey.get()).isEqualTo("test-key");
      assertThat(body.get()).contains("name=\"image\"", "Content-Type: image/png");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void repeatedServerErrorsPauseTheCallsUntilTheServiceRecovers() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger hits = new AtomicInteger();
    server.createContext("/work", exchange -> {
      hits.incrementAndGet();
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });
    server.start();
    try {
      AiServiceGateway gateway = gateway(server);
      for (int i = 0; i < 5; i++) {
        assertThatThrownBy(() -> gateway.postJson("/work", Map.of(), Map.class))
            .isInstanceOf(AiServiceException.class);
      }
      assertThat(hits.get()).isEqualTo(5);

      assertThatThrownBy(() -> gateway.postJson("/work", Map.of(), Map.class))
          .isInstanceOf(AiServiceException.class).hasMessageContaining("paused");
      assertThat(hits.get()).isEqualTo(5);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void clientErrorsAndRejectionsDoNotPauseTheCalls() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger hits = new AtomicInteger();
    server.createContext("/work", exchange -> {
      hits.incrementAndGet();
      exchange.sendResponseHeaders(422, -1);
      exchange.close();
    });
    server.start();
    try {
      AiServiceGateway gateway = gateway(server);
      for (int i = 0; i < 12; i++) {
        assertThatThrownBy(() -> gateway.postJson("/work", Map.of(), Map.class))
            .isInstanceOf(AiRequestRejectedException.class);
      }
      assertThat(hits.get()).isEqualTo(12);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void passesTheRequestIdOfTheCurrentRequestOn() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> requestId = new AtomicReference<>();
    server.createContext("/echo", exchange -> {
      requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
      byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      gateway(server).postJson("/echo", Map.of(), Map.class);
      assertThat(requestId.get()).isNull();

      MDC.put("requestId", "req-123");
      try {
        gateway(server).postJson("/echo", Map.of(), Map.class);
      } finally {
        MDC.remove("requestId");
      }
      assertThat(requestId.get()).isEqualTo("req-123");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void serviceAuthenticationFailureIsNotReportedAsARejectedRequest() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/upload", exchange -> {
      exchange.sendResponseHeaders(401, -1);
      exchange.close();
    });
    server.start();
    try {
      assertThatThrownBy(
          () -> gateway(server).postMultipart("/upload", new LinkedMultiValueMap<>(), Map.class))
          .isInstanceOf(AiServiceException.class).isNotInstanceOf(AiRequestRejectedException.class);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectedRequestCarriesTheServiceExplanation() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/diagnose", exchange -> {
      byte[] response = "{\"detail\":\"Image too blurry\"}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(422, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      assertThatThrownBy(() -> gateway(server).postJson("/diagnose", Map.of(), Map.class))
          .isInstanceOfSatisfying(AiRequestRejectedException.class, e -> {
            assertThat(e.status()).isEqualTo(422);
            assertThat(e.detail()).isEqualTo("Image too blurry");
          });
    } finally {
      server.stop(0);
    }
  }
}
