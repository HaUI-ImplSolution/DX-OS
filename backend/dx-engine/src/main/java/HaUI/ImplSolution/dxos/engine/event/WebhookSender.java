// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.engine.event.WebhookTargets.Target;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// Posts one message to a webhook target. A json target gets the message as it is and a chat target
// gets {"text": ...} with the sentence given. X-Dxos-Event-Id carries the id of the message so a
// receiver can drop a repeat, and X-Dxos-Signature carries the HMAC-SHA256 of the body when the
// target has a secret. Any answer but 2xx fails the try and the outbox sends it again.
@Component
public class WebhookSender {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final ObjectMapper json;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  public WebhookSender(ObjectMapper json) {
    this.json = json;
  }

  public void send(Target target, String id, Map<String, Object> message, String sentence) {
    String body = json
        .writeValueAsString("chat".equals(target.format()) ? Map.of("text", sentence) : message);
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target.url())).timeout(TIMEOUT)
        .header("Content-Type", "application/json").header("X-Dxos-Event-Id", id)
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (!target.secret().isEmpty()) {
      request.header("X-Dxos-Signature", "sha256=" + sign(body, target.secret()));
    }
    CompletableFuture<HttpResponse<Void>> pending =
        client.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding());
    try {
      // The timeout of the request ends when the headers arrive, so the wait for the whole answer
      // is limited here.
      int status = pending.orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS).get().statusCode();
      if (status < 200 || status >= 300) {
        throw new IllegalStateException("The webhook answered " + status);
      }
    } catch (ExecutionException e) {
      pending.cancel(true);
      if (e.getCause() instanceof IOException || e.getCause() instanceof TimeoutException) {
        throw new TargetUnreachableException(
            "The webhook could not be reached (" + e.getCause().getClass().getSimpleName() + ")");
      }
      throw new IllegalStateException("The webhook could not be posted", e.getCause());
    } catch (InterruptedException e) {
      pending.cancel(true);
      Thread.currentThread().interrupt();
      throw new TargetUnreachableException("Interrupted while posting to the webhook");
    }
  }

  private static String sign(String body, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not sign the webhook body", e);
    }
  }
}
