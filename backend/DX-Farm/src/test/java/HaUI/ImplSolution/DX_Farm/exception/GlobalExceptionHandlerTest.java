package HaUI.ImplSolution.DX_Farm.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

  @Test
  void aiServiceFailureReturnsServiceUnavailableWithUserFacingMessage() {
    var response = new GlobalExceptionHandler()
        .handleAiService(new AiServiceException("upstream detail", new RuntimeException()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().success()).isFalse();
    assertThat(response.getBody().message())
        .isEqualTo("Dịch vụ chẩn đoán AI tạm thời không khả dụng. Vui lòng thử lại sau.");
  }
}
