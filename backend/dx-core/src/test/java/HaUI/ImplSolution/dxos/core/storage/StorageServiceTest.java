// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Signing is local computation, so no storage server is needed here.
class StorageServiceTest {

  private StorageService service;

  @BeforeEach
  void setUp() {
    service = new StorageService("http://seaweedfs:8333", "access", "secret", "photos",
        "http://gateway.example:9080", Duration.ofMinutes(15));
  }

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  private static void requestFrom(String host) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Host", host);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @Test
  void linkIsSignedForTheHostTheCallerUsed() {
    requestFrom("192.168.1.10:9080");

    String url = service.presignedUrl("leaf.jpg");

    assertThat(url).startsWith("http://192.168.1.10:9080/photos/leaf.jpg?")
        .contains("X-Amz-Signature=").contains("X-Amz-Expires=900");
  }

  @Test
  void linkFallsBackToTheConfiguredOriginOutsideARequest() {
    assertThat(service.presignedUrl("leaf.jpg"))
        .startsWith("http://gateway.example:9080/photos/leaf.jpg?");
  }

  @Test
  void malformedHostHeaderIsIgnored() {
    requestFrom("evil.example/../x@y");

    assertThat(service.presignedUrl("leaf.jpg"))
        .startsWith("http://gateway.example:9080/photos/leaf.jpg?");
  }

  @Test
  void linkForTheAiServiceUsesTheInternalEndpoint() {
    assertThat(service.internalPresignedUrl("leaf.jpg", Duration.ofMinutes(10)))
        .startsWith("http://seaweedfs:8333/photos/leaf.jpg?").contains("X-Amz-Expires=600");
  }
}
