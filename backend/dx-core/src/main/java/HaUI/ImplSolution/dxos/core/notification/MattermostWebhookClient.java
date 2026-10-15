// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.notification;

import HaUI.ImplSolution.dxos.core.api.notification.ChatWebhook;
import HaUI.ImplSolution.dxos.core.api.notification.NotificationChannel;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
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

// Posts to a Mattermost incoming webhook. A channel's URL comes from mattermost.webhook.url-<key>,
// then <mattermost.webhook.dir>/<key>.url (read on every use), then the default url or default.url;
// with none, its notifications are off. URLs carry the access key, so they are never logged.
@Component
public class MattermostWebhookClient implements ChatWebhook {

  private static final Logger log = LoggerFactory.getLogger(MattermostWebhookClient.class);
  private static final String DEFAULT_KEY = "default";

  private final Map<String, Optional<URI>> propertyUris = new ConcurrentHashMap<>();
  private final Environment environment;
  private final URI defaultUri;
  private final Path webhookDir;
  private final RestClient restClient;

  public MattermostWebhookClient(Environment environment,
      ObjectProvider<NotificationChannel> channels, @Value("${mattermost.webhook.url:}") String url,
      @Value("${mattermost.webhook.dir:}") String dir,
      @Value("${mattermost.webhook.connect-timeout-ms:2000}") int connectTimeoutMs,
      @Value("${mattermost.webhook.read-timeout-ms:3000}") int readTimeoutMs) {
    this.environment = environment;
    this.defaultUri = parse(url, true);
    this.webhookDir = dir == null || dir.isBlank() ? null : Path.of(dir.strip());
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    channels.orderedStream().forEach(channel -> log.info("Mattermost webhook for {}: {}",
        channel.key(), isEnabled(channel.key()) ? "configured" : "not configured yet"));
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
    String key = channelKey.toLowerCase(Locale.ROOT);
    Optional<URI> own = propertyUris.computeIfAbsent(key, k -> Optional
        .ofNullable(parse(environment.getProperty("mattermost.webhook.url-" + k, ""), true)));
    if (own.isPresent()) {
      return own;
    }
    URI fromFile = fromFile(key);
    if (fromFile != null) {
      return Optional.of(fromFile);
    }
    if (defaultUri != null) {
      return Optional.of(defaultUri);
    }
    return Optional.ofNullable(fromFile(DEFAULT_KEY));
  }

  private URI fromFile(String key) {
    if (webhookDir == null || !key.matches("[a-z0-9_-]+")) {
      return null;
    }
    try {
      return parse(Files.readString(webhookDir.resolve(key + ".url")), false);
    } catch (IOException e) {
      return null;
    }
  }

  private static URI parse(String url, boolean warn) {
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
    if (warn) {
      log.warn("A mattermost.webhook url is not a valid http(s) URL; it is ignored");
    }
    return null;
  }
}
