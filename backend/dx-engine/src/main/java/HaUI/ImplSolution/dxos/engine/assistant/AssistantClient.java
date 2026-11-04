// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.assistant;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AssistantClient {
  private final AssistantSettings settings;
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(3)).build();

  public AssistantClient(AssistantSettings settings) {
    this.settings = settings;
  }

  public <T> T call(String operation, Object payload, Class<T> type, long deadline) {
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0 || settings.serviceUrl().isBlank() || settings.serviceKey().isBlank()) {
      throw new IllegalStateException("Assistant is not available");
    }
    URI uri = URI.create(settings.serviceUrl());
    if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
        || uri.getUserInfo() != null) {
      throw new IllegalStateException("Assistant URL is invalid");
    }
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofNanos(remaining));
    T result = RestClient.builder().baseUrl(settings.serviceUrl()).requestFactory(factory).build()
        .post().uri("/api/v1/assistant/" + operation)
        .header("X-Service-Api-Key", settings.serviceKey())
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(payload).retrieve()
        .body(type);
    if (result == null || System.nanoTime() >= deadline) {
      throw new IllegalStateException("Assistant deadline exceeded");
    }
    return result;
  }
}
