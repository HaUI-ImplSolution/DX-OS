// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import static org.assertj.core.api.Assertions.*;

import HaUI.ImplSolution.DX_Farm.exception.AiServiceException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AiDiagnosisClientPrecheckTest {
  @Test
  void forwardsSanitizedImageAsAuthenticatedMultipart() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> contentType = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    AtomicReference<String> apiKey = new AtomicReference<>();
    server.createContext("/api/v1/disease/precheck", exchange -> {
      contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
      apiKey.set(exchange.getRequestHeaders().getFirst("X-Service-Api-Key"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
      byte[] response =
          "{\"passed\":true,\"reasons\":[],\"width\":256,\"height\":256,\"sharpness\":100,\"brightness\":120}"
              .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      var client = new AiDiagnosisClient("http://127.0.0.1:" + server.getAddress().getPort(),
          "test-key", 1000, 3000, new ObjectMapper());
      assertThat(client.precheck(new byte[] {(byte) 0x89, 'P', 'N', 'G'}).passed()).isTrue();
      assertThat(contentType.get()).startsWith("multipart/form-data");
      assertThat(apiKey.get()).isEqualTo("test-key");
      assertThat(body.get()).contains("name=\"image\"", "Content-Type: image/png");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void serviceAuthenticationFailureIsNotReportedAsPoorImageQuality() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/disease/precheck", exchange -> {
      exchange.sendResponseHeaders(401, -1);
      exchange.close();
    });
    server.start();
    try {
      var client = new AiDiagnosisClient("http://127.0.0.1:" + server.getAddress().getPort(),
          "test-key", 1000, 3000, new ObjectMapper());
      assertThatThrownBy(() -> client.precheck(new byte[] {1}))
          .isInstanceOf(AiServiceException.class);
    } finally {
      server.stop(0);
    }
  }
}
