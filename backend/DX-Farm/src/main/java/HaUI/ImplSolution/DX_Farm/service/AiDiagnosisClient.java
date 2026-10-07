package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseRequest;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseResponse;
import HaUI.ImplSolution.DX_Farm.dto.ImagePrecheckResponse;
import HaUI.ImplSolution.DX_Farm.dto.SymptomSuggestionResponse;
import HaUI.ImplSolution.DX_Farm.exception.AiServiceException;
import HaUI.ImplSolution.DX_Farm.exception.AiTriageException;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Calls the AI service: diagnosis, image quality precheck and symptom ranking.
@Service
public class AiDiagnosisClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public AiDiagnosisClient(@Value("${ai.service.base-url}") String baseUrl,
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

  @CircuitBreaker(name = "aiDiagnosis", fallbackMethod = "diagnoseFallback")
  public AiDiagnoseResponse diagnose(AiDiagnoseRequest request) {
    try {
      return restClient.post().uri("/api/v1/diagnose").body(request).retrieve()
          .body(AiDiagnoseResponse.class);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 422) {
        throw new AiTriageException(readTriageMessage(e), e);
      }
      throw new AiServiceException("AI diagnose call failed: " + e.getMessage(), e);
    } catch (RestClientException e) {
      throw new AiServiceException("AI diagnose call failed: " + e.getMessage(), e);
    }
  }

  public ImagePrecheckResponse precheck(byte[] image) {
    var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("image", new ByteArrayResource(image) {
      @Override
      public String getFilename() {
        return "image." + ((image[0] & 0xFF) == 0xFF ? "jpg" : "png");
      }
    });
    try {
      ImagePrecheckResponse response = restClient.post().uri("/api/v1/disease/precheck")
          .contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve()
          .body(ImagePrecheckResponse.class);
      if (response == null) {
        throw new AiServiceException("Empty precheck response", null);
      }
      return response;
    } catch (RestClientResponseException e) {
      if (Set.of(400, 413, 422).contains(e.getStatusCode().value())) {
        throw new AiTriageException(readTriageMessage(e), e);
      }
      throw new AiServiceException("AI precheck unavailable", e);
    } catch (RestClientException e) {
      throw new AiServiceException("AI precheck unavailable", e);
    }
  }

  public List<SymptomSuggestionResponse> suggest(String symptomText) {
    try {
      var response = restClient.post().uri("/api/v1/disease/symptoms/suggest")
          .body(Map.of("symptom_text", symptomText, "top_k", 6)).retrieve()
          .body(new ParameterizedTypeReference<List<SymptomSuggestionResponse>>() {});
      if (response == null) {
        throw new AiServiceException("Empty suggestions response", null);
      }
      return response;
    } catch (RestClientException e) {
      throw new AiServiceException("AI suggestions unavailable", e);
    }
  }

  public List<KnowledgeChunk> retrieveKnowledge(String query, String diseaseCode, int topK) {
    Map<String, Object> body = new HashMap<>();
    body.put("query", query);
    body.put("top_k", topK);
    if (diseaseCode != null) {
      body.put("disease_code", diseaseCode);
    }
    try {
      var response = restClient.post().uri("/api/v1/disease/knowledge/retrieve").body(body)
          .retrieve().body(new ParameterizedTypeReference<List<KnowledgeChunk>>() {});
      if (response == null) {
        throw new AiServiceException("Empty knowledge response", null);
      }
      return response;
    } catch (RestClientException e) {
      throw new AiServiceException("AI knowledge retrieval unavailable", e);
    }
  }

  // One passage returned by the AI retriever.
  public record KnowledgeChunk(String content, @JsonProperty("document_title") String documentTitle,
      @JsonProperty("source_ids") List<String> sourceIds,
      @JsonProperty("disease_code") String diseaseCode, double score) {

    public KnowledgeChunk {
      sourceIds = sourceIds == null ? List.of() : sourceIds;
    }
  }

  private AiDiagnoseResponse diagnoseFallback(AiDiagnoseRequest request, Throwable t) {
    if (t instanceof AiTriageException triageException) {
      throw triageException;
    }
    throw new AiServiceException("AI diagnosis service unavailable", t);
  }

  private String readTriageMessage(RestClientResponseException exception) {
    try {
      JsonNode detail = objectMapper.readTree(exception.getResponseBodyAsString()).path("detail");
      if (detail.isTextual() && !detail.asText().isBlank()) {
        return detail.asText();
      }
    } catch (Exception ignored) {
      // Fall through to a stable user-facing message if the AI error body is malformed.
    }
    return "Ảnh chưa đạt yêu cầu nhận diện. Vui lòng chụp lại ảnh lá lúa rõ nét.";
  }
}
