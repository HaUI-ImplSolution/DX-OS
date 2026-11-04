// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// Posts the history lines of records to the webhook address as JSON. X-Dxos-Event-Id carries the id
// of the line so a receiver can drop a repeat, and X-Dxos-Signature carries the HMAC-SHA256 of the
// body when a secret is set. Any answer but 2xx is retried by the outbox, and lines may arrive out
// of order, so the receiver orders them by eventId.
@Component
public class WebhookHandler implements OutboxHandler {

  public static final String TOPIC = "record.event";

  private static final Logger log = LoggerFactory.getLogger(WebhookHandler.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final ObjectMapper json;
  private final String url;
  private final String secret;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  public WebhookHandler(ObjectMapper json, @Value("${engine.webhook.url:}") String url,
      @Value("${engine.webhook.secret:}") String secret) {
    this.json = json;
    this.url = usable(url) ? url.strip() : "";
    this.secret = secret;
    if (this.url.isEmpty() && !url.isBlank()) {
      log.error("ENGINE_WEBHOOK_URL is not an http or https URL with a host, so no event is sent");
    }
  }

  // An http or https address with a host; the address itself is never logged since it may hold a
  // key.
  static boolean usable(String address) {
    try {
      URI uri = URI.create(address.strip());
      return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
          || "https".equalsIgnoreCase(uri.getScheme()));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public void deliver(Map<String, Object> payload) {
    if (url.isEmpty()) {
      return;
    }
    String body = json.writeValueAsString(payload);
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
        .header("Content-Type", "application/json")
        .header("X-Dxos-Event-Id", String.valueOf(payload.get("eventId")))
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (!secret.isEmpty()) {
      request.header("X-Dxos-Signature", "sha256=" + sign(body));
    }
    try {
      int status =
          client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
      if (status < 200 || status >= 300) {
        throw new IllegalStateException("The webhook answered " + status);
      }
    } catch (IOException e) {
      throw new TargetUnreachableException(
          "The webhook could not be reached (" + e.getClass().getSimpleName() + ")");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TargetUnreachableException("Interrupted while posting to the webhook");
    }
  }

  private String sign(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not sign the webhook body", e);
    }
  }
}
