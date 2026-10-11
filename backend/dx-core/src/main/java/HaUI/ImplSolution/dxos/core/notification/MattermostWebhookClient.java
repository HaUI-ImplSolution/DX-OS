// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.notification;

import HaUI.ImplSolution.dxos.core.api.notification.ChatWebhook;
import HaUI.ImplSolution.dxos.core.api.notification.NotificationChannel;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// Posts a message to a Mattermost Incoming Webhook. A channel without its own URL
// (mattermost.webhook.url-<key>) uses the default one (mattermost.webhook.url); with neither,
// notifications for it are off. A URL carries the access key, so it is never logged and exception
// messages are not logged (they can contain the request URL).
@Component
public class MattermostWebhookClient implements ChatWebhook {

  private static final Logger log = LoggerFactory.getLogger(MattermostWebhookClient.class);

  private final Map<String, Optional<URI>> channelUris = new ConcurrentHashMap<>();
  private final Environment environment;
  private final URI defaultUri;
  private final RestClient restClient;

  public MattermostWebhookClient(Environment environment,
      ObjectProvider<NotificationChannel> channels, @Value("${mattermost.webhook.url:}") String url,
      @Value("${mattermost.webhook.connect-timeout-ms:2000}") int connectTimeoutMs,
      @Value("${mattermost.webhook.read-timeout-ms:3000}") int readTimeoutMs) {
    this.environment = environment;
    this.defaultUri = parse(url);
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    channels.orderedStream()
        .forEach(channel -> log.info("Mattermost webhook notifications for {} {}", channel.key(),
            isEnabled(channel.key()) ? "enabled" : "disabled"));
  }

  @Override
  public boolean isEnabled(String channelKey) {
    return uriOf(channelKey).isPresent();
  }

  @Override
  public void send(String channelKey, Map<String, Object> payload) {
    Optional<URI> uri = uriOf(channelKey);
    if (uri.isEmpty()) {
      return;
    }
    try {
      restClient.post().uri(uri.get()).contentType(MediaType.APPLICATION_JSON).body(payload)
          .retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      log.warn("Mattermost webhook rejected the notification: HTTP {}", e.getStatusCode().value());
    } catch (Exception e) {
      log.warn("Mattermost webhook call failed: {}", e.getClass().getSimpleName());
    }
  }

  private Optional<URI> uriOf(String channelKey) {
    return channelUris.computeIfAbsent(channelKey, key -> {
      URI own = parse(
          environment.getProperty("mattermost.webhook.url-" + key.toLowerCase(Locale.ROOT), ""));
      return Optional.ofNullable(own != null ? own : defaultUri);
    });
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
