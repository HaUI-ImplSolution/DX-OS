package HaUI.ImplSolution.DX_Farm.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// Posts a message to a Mattermost Incoming Webhook. Disabled when the URL is empty or invalid.
// The URL carries the access key, so it is never logged and exception messages are not logged
// (they can contain the request URL).
@Component
public class MattermostWebhookClient {

  private static final Logger log = LoggerFactory.getLogger(MattermostWebhookClient.class);

  private final URI webhookUri;
  private final RestClient restClient;

  public MattermostWebhookClient(@Value("${mattermost.webhook.url:}") String url,
      @Value("${mattermost.webhook.connect-timeout-ms:2000}") int connectTimeoutMs,
      @Value("${mattermost.webhook.read-timeout-ms:3000}") int readTimeoutMs) {
    this.webhookUri = parse(url);
    if (webhookUri == null) {
      this.restClient = null;
    } else {
      HttpClient httpClient =
          HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
      JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
      requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
      this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }
    log.info("Mattermost webhook notifications {}", isEnabled() ? "enabled" : "disabled");
  }

  public boolean isEnabled() {
    return restClient != null;
  }

  public void send(Map<String, Object> payload) {
    if (!isEnabled()) {
      return;
    }
    try {
      restClient.post().uri(webhookUri).contentType(MediaType.APPLICATION_JSON).body(payload)
          .retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      log.warn("Mattermost webhook rejected the notification: HTTP {}", e.getStatusCode().value());
    } catch (Exception e) {
      log.warn("Mattermost webhook call failed: {}", e.getClass().getSimpleName());
    }
  }

  private static URI parse(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(url.strip());
      if (uri.getHost() != null
          && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
        return uri;
      }
    } catch (IllegalArgumentException e) {
      // Falls through to the disabled state; the message would expose the URL.
    }
    log.warn("mattermost.webhook.url is not a valid http(s) URL; notifications are disabled");
    return null;
  }
}
