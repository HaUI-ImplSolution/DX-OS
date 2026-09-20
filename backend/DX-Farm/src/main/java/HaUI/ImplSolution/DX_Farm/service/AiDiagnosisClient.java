package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseRequest;
import HaUI.ImplSolution.DX_Farm.dto.AiDiagnoseResponse;
import HaUI.ImplSolution.DX_Farm.exception.AiServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Gọi inference contract POST /api/v1/diagnose của AI Service.
// Chưa được gọi từ flow nào - nối vào DiseaseReportService ở P2-4.
@Service
public class AiDiagnosisClient {

  private final RestClient restClient;

  public AiDiagnosisClient(@Value("${ai.service.base-url}") String baseUrl,
      @Value("${ai.service.api-key:}") String apiKey,
      @Value("${ai.service.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${ai.service.read-timeout-ms:15000}") int readTimeoutMs) {
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
    } catch (RestClientException e) {
      throw new AiServiceException("AI diagnose call failed: " + e.getMessage(), e);
    }
  }

  private AiDiagnoseResponse diagnoseFallback(AiDiagnoseRequest request, Throwable t) {
    throw new AiServiceException("AI diagnosis service unavailable", t);
  }
}
