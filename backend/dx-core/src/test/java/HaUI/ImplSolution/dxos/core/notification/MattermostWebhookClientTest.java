// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

class MattermostWebhookClientTest {

  private final ChatOutbox outbox = mock(ChatOutbox.class);
  private HttpServer server;
  private volatile int status;
  private volatile String lastBody = "";

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/hooks/abc", exchange -> {
      lastBody = new String(exchange.getRequestBody().readAllBytes(),
          java.nio.charset.StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @SuppressWarnings("unchecked")
  private MattermostWebhookClient client(String url) {
    return new MattermostWebhookClient(new MockEnvironment(), outbox, mock(ObjectProvider.class),
        url, "", 500, 1000);
  }

  private String hook() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/hooks/abc";
  }

  @Test
  void deliveredMessageIsNotKept() {
    status = 200;

    client(hook()).send("engineer", Map.of("text", "hi"));

    verify(outbox, never()).enqueue(any(), any(), any());
  }

  @Test
  void personalDataIsHiddenBeforeTheMessageLeaves() {
    status = 200;

    client(hook()).send("engineer", Map.of("text", "Máy hỏng, gọi 0901234567 hoặc a@b.vn"));

    assertThat(lastBody).contains("Máy hỏng, gọi [đã ẩn] hoặc [đã ẩn]")
        .doesNotContain("0901234567");
  }

  @Test
  void keptMessageIsAlreadyMasked() {
    status = 503;

    client(hook()).send("engineer", Map.of("text", "gọi 0901234567"));

    verify(outbox).enqueue("engineer", Map.of("text", "gọi [đã ẩn]"), "HTTP 503");
  }

  @Test
  void serverErrorIsKeptToBeSentAgain() {
    status = 503;
    Map<String, Object> payload = Map.of("text", "hi");

    client(hook()).send("engineer", payload);

    verify(outbox).enqueue("engineer", payload, "HTTP 503");
  }

  @Test
  void unreachableWebhookIsKeptToBeSentAgain() {
    MattermostWebhookClient client = client("http://127.0.0.1:1/hooks/abc");

    client.send("engineer", Map.of("text", "hi"));

    verify(outbox).enqueue(eq("engineer"), any(), any());
  }

  @Test
  void rejectedMessageIsDroppedBecauseSendingItAgainChangesNothing() {
    status = 400;

    client(hook()).send("engineer", Map.of("text", "hi"));

    verify(outbox, never()).enqueue(any(), any(), any());
  }

  @Test
  void channelWithoutWebhookIsSkipped() {
    client("").send("engineer", Map.of("text", "hi"));

    verify(outbox, never()).enqueue(any(), any(), any());
  }
}
