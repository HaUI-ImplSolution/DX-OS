package HaUI.ImplSolution.DX_Farm.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// Posts a message to a Mattermost Incoming Webhook. A channel without its own URL uses the default
// one; with neither, notifications for it are off. A URL carries the access key, so it is never
// logged and exception messages are not logged (they can contain the request URL).
@Component
public class MattermostWebhookClient {

  private static final Logger log = LoggerFactory.getLogger(MattermostWebhookClient.class);

  private final Map<MattermostChannel, URI> channelUris = new EnumMap<>(MattermostChannel.class);
  private final RestClient restClient;

  public MattermostWebhookClient(@Value("${mattermost.webhook.url:}") String url,
      @Value("${mattermost.webhook.url-admin:}") String adminUrl,
      @Value("${mattermost.webhook.url-engineer:}") String engineerUrl,
      @Value("${mattermost.webhook.connect-timeout-ms:2000}") int connectTimeoutMs,
      @Value("${mattermost.webhook.read-timeout-ms:3000}") int readTimeoutMs) {
    URI defaultUri = parse(url);
    URI admin = parse(adminUrl);
    URI engineer = parse(engineerUrl);
    putIfPresent(MattermostChannel.ADMIN, admin != null ? admin : defaultUri);
    putIfPresent(MattermostChannel.ENGINEER, engineer != null ? engineer : defaultUri);
    if (channelUris.isEmpty()) {
      this.restClient = null;
    } else {
      HttpClient httpClient =
          HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
      JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
      requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
      this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }
    for (MattermostChannel channel : MattermostChannel.values()) {
      log.info("Mattermost webhook notifications for {} {}", channel,
          isEnabled(channel) ? "enabled" : "disabled");
    }
  }

  public boolean isEnabled(MattermostChannel channel) {
    return restClient != null && channelUris.containsKey(channel);
  }

  public void send(MattermostChannel channel, Map<String, Object> payload) {
    if (!isEnabled(channel)) {
      return;
    }
    try {
      restClient.post().uri(channelUris.get(channel)).contentType(MediaType.APPLICATION_JSON)
          .body(payload).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      log.warn("Mattermost webhook rejected the notification: HTTP {}", e.getStatusCode().value());
    } catch (Exception e) {
      log.warn("Mattermost webhook call failed: {}", e.getClass().getSimpleName());
    }
  }

  private void putIfPresent(MattermostChannel channel, URI uri) {
    if (uri != null) {
      channelUris.put(channel, uri);
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
    log.warn("A mattermost.webhook url is not a valid http(s) URL; it is ignored");
    return null;
  }
}
