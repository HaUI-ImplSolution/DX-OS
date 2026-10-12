// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.ai;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.exception.AiRequestRejectedException;
import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// HTTP client of the AI service: attaches the service key and turns failures into
// AiServiceException.
@Component
public class AiServiceGateway implements AiGateway {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public AiServiceGateway(@Value("${ai.service.base-url}") String baseUrl,
      @Value("${ai.service.api-key:}") String apiKey,
      @Value("${ai.service.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${ai.service.read-timeout-ms:15000}") int readTimeoutMs, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

    RestClient.Builder builder =
        RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
    if (StringUtils.hasText(apiKey)) {
      builder.defaultHeader("X-Service-Api-Key", apiKey);
    }
    this.restClient = builder.build();
  }

  @Override
  public boolean isAvailable() {
    try {
      restClient.get().uri("/api/v1/disease/health").retrieve().toBodilessEntity();
      return true;
    } catch (RestClientException e) {
      return false;
    }
  }

  @Override
  public <T> T postJson(String path, Object body, Class<T> responseType) {
    try {
      return required(restClient.post().uri(path).body(body).retrieve().body(responseType), path);
    } catch (RestClientResponseException e) {
      throw translate(path, e);
    } catch (RestClientException e) {
      throw new AiServiceException("AI call failed: " + path, e);
    }
  }

  @Override
  public <T> T postJson(String path, Object body, ParameterizedTypeReference<T> responseType) {
    try {
      return required(restClient.post().uri(path).body(body).retrieve().body(responseType), path);
    } catch (RestClientResponseException e) {
      throw translate(path, e);
    } catch (RestClientException e) {
      throw new AiServiceException("AI call failed: " + path, e);
    }
  }

  @Override
  public <T> T postMultipart(String path, MultiValueMap<String, Object> parts,
      Class<T> responseType) {
    try {
      return required(restClient.post().uri(path).contentType(MediaType.MULTIPART_FORM_DATA)
          .body(parts).retrieve().body(responseType), path);
    } catch (RestClientResponseException e) {
      throw translate(path, e);
    } catch (RestClientException e) {
      throw new AiServiceException("AI call failed: " + path, e);
    }
  }

  private static <T> T required(T response, String path) {
    if (response == null) {
      throw new AiServiceException("Empty AI response: " + path, null);
    }
    return response;
  }

  private AiServiceException translate(String path, RestClientResponseException e) {
    int status = e.getStatusCode().value();
    if (e.getStatusCode().is4xxClientError() && status != 401 && status != 403) {
      return new AiRequestRejectedException(status, detailOf(e), e);
    }
    return new AiServiceException("AI call failed: " + path + " (HTTP " + status + ")", e);
  }

  // The "detail" text of a FastAPI error body, if there is one.
  private String detailOf(RestClientResponseException exception) {
    try {
      JsonNode detail = objectMapper.readTree(exception.getResponseBodyAsString()).path("detail");
      if (detail.isTextual() && !detail.asText().isBlank()) {
        return detail.asText();
      }
    } catch (Exception ignored) {
      // A malformed error body carries no usable detail.
    }
    return null;
  }
}
