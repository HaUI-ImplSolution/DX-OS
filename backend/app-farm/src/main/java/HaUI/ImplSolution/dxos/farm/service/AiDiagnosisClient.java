// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.exception.AiRequestRejectedException;
import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import HaUI.ImplSolution.dxos.core.api.privacy.PiiMask;
import HaUI.ImplSolution.dxos.farm.dto.AiDiagnoseRequest;
import HaUI.ImplSolution.dxos.farm.dto.AiDiagnoseResponse;
import HaUI.ImplSolution.dxos.farm.dto.ImagePrecheckResponse;
import HaUI.ImplSolution.dxos.farm.dto.SymptomSuggestionResponse;
import HaUI.ImplSolution.dxos.farm.exception.AiTriageException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

// The AI calls of the disease flow: diagnosis, image quality precheck and symptom ranking.
@Service
public class AiDiagnosisClient {

  private static final String DEFAULT_TRIAGE_MESSAGE =
      "Ảnh chưa đạt yêu cầu nhận diện. Vui lòng chụp lại ảnh lá lúa rõ nét.";

  private final AiGateway aiGateway;

  public AiDiagnosisClient(AiGateway aiGateway) {
    this.aiGateway = aiGateway;
  }

  @CircuitBreaker(name = "aiDiagnosis", fallbackMethod = "diagnoseFallback")
  public AiDiagnoseResponse diagnose(AiDiagnoseRequest request) {
    try {
      return aiGateway.postJson("/api/v1/diagnose", request, AiDiagnoseResponse.class);
    } catch (AiRequestRejectedException e) {
      if (e.status() == 422) {
        throw new AiTriageException(triageMessage(e), e);
      }
      throw e;
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
      return aiGateway.postMultipart("/api/v1/disease/precheck", parts,
          ImagePrecheckResponse.class);
    } catch (AiRequestRejectedException e) {
      if (Set.of(400, 413, 422).contains(e.status())) {
        throw new AiTriageException(triageMessage(e), e);
      }
      throw new AiServiceException("AI precheck unavailable", e);
    } catch (AiServiceException e) {
      throw new AiServiceException("AI precheck unavailable", e);
    }
  }

  public List<SymptomSuggestionResponse> suggest(String symptomText) {
    try {
      return aiGateway.postJson("/api/v1/disease/symptoms/suggest",
          Map.of("symptom_text", PiiMask.mask(symptomText), "top_k", 6),
          new ParameterizedTypeReference<List<SymptomSuggestionResponse>>() {});
    } catch (AiServiceException e) {
      throw new AiServiceException("AI suggestions unavailable", e);
    }
  }

  private AiDiagnoseResponse diagnoseFallback(AiDiagnoseRequest request, Throwable t) {
    if (t instanceof AiTriageException triageException) {
      throw triageException;
    }
    throw new AiServiceException("AI diagnosis service unavailable", t);
  }

  private static String triageMessage(AiRequestRejectedException exception) {
    return exception.detail() != null ? exception.detail() : DEFAULT_TRIAGE_MESSAGE;
  }
}
