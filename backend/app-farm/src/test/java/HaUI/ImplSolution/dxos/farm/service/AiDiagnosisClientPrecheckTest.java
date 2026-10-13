// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.exception.AiRequestRejectedException;
import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import HaUI.ImplSolution.dxos.farm.dto.ImagePrecheckResponse;
import HaUI.ImplSolution.dxos.farm.exception.AiTriageException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.util.MultiValueMap;

class AiDiagnosisClientPrecheckTest {

  // Answers every multipart call with the configured result or failure.
  private static final class FakeGateway implements AiGateway {
    final AtomicReference<String> path = new AtomicReference<>();
    final AtomicReference<String> filename = new AtomicReference<>();
    RuntimeException failure;

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public <T> T postJson(String path, Object body, Class<T> responseType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T postJson(String path, Object body, ParameterizedTypeReference<T> responseType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T postMultipart(String path, MultiValueMap<String, Object> parts,
        Class<T> responseType) {
      this.path.set(path);
      this.filename.set(((Resource) parts.getFirst("image")).getFilename());
      if (failure != null) {
        throw failure;
      }
      return responseType
          .cast(new ImagePrecheckResponse(true, java.util.List.of(), 256, 256, 100.0, 120.0));
    }
  }

  @Test
  void forwardsSanitizedImageAsMultipartNamedByItsFormat() {
    FakeGateway gateway = new FakeGateway();
    var client = new AiDiagnosisClient(gateway);

    assertThat(client.precheck(new byte[] {(byte) 0x89, 'P', 'N', 'G'}).passed()).isTrue();

    assertThat(gateway.path.get()).isEqualTo("/api/v1/disease/precheck");
    assertThat(gateway.filename.get()).isEqualTo("image.png");
  }

  @Test
  void serviceFailureIsNotReportedAsPoorImageQuality() {
    FakeGateway gateway = new FakeGateway();
    gateway.failure = new AiServiceException("AI call failed", null);
    var client = new AiDiagnosisClient(gateway);

    assertThatThrownBy(() -> client.precheck(new byte[] {1})).isInstanceOf(AiServiceException.class)
        .isNotInstanceOf(AiTriageException.class);
  }

  @Test
  void rejectedImageIsReportedWithTheServiceExplanation() {
    FakeGateway gateway = new FakeGateway();
    gateway.failure = new AiRequestRejectedException(422, "Ảnh bị mờ", null);
    var client = new AiDiagnosisClient(gateway);

    assertThatThrownBy(() -> client.precheck(new byte[] {1})).isInstanceOf(AiTriageException.class)
        .hasMessage("Ảnh bị mờ");
  }
}
