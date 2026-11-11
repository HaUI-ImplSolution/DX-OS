// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import HaUI.ImplSolution.dxos.engine.event.WebhookTargets.Target;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

// What the outbox relies on: a message counts as sent only after a 2xx answer, the signature lets
// the receiver trust the body, and a target that cannot be reached is told from one that answered.
class WebhookSenderTest {

  @RegisterExtension
  static WireMockExtension receiver =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private final WebhookSender sender = new WebhookSender(JsonMapper.builder().build());

  private static Target target(String url, String secret) {
    return new Target(url, secret, "json");
  }

  private static String hmac(String body, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void theSignatureCoversTheExactBodyThatWasSent() throws Exception {
    receiver.stubFor(post(urlEqualTo("/hook")).willReturn(aResponse().withStatus(200)));

    sender.send(target(receiver.url("/hook"), "s3cret"), "42", Map.of("action", "created"), "");

    receiver
        .verify(postRequestedFor(urlEqualTo("/hook")).withHeader("X-Dxos-Event-Id", equalTo("42")));
    LoggedRequest sent = receiver.findAll(postRequestedFor(urlEqualTo("/hook"))).get(0);
    assertThat(sent.getHeader("X-Dxos-Signature"))
        .isEqualTo("sha256=" + hmac(sent.getBodyAsString(), "s3cret"));
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 302})
  void anAnswerThatIsNotSuccessIsNotTakenAsSent(int status) {
    receiver.stubFor(post(urlEqualTo("/hook")).willReturn(aResponse().withStatus(status)));

    assertThatThrownBy(() -> sender.send(target(receiver.url("/hook"), ""), "1", Map.of(), ""))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("answered " + status);
  }

  @Test
  void aClosedPortIsUnreachableSoTheOutboxStopsTheTopic() throws Exception {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }

    assertThatThrownBy(() -> sender.send(target("http://127.0.0.1:" + closedPort + "/hook", ""),
        "1", Map.of(), "")).isInstanceOf(TargetUnreachableException.class);
  }

  // Takes about five seconds, the time the sender waits; leave it out with -DexcludedGroups=slow.
  @Test
  @Tag("slow")
  void slowAnswerIsGivenUpAfterTheTimeoutInsteadOfHanging() {
    receiver.stubFor(
        post(urlEqualTo("/hook")).willReturn(aResponse().withStatus(200).withFixedDelay(8000)));
    long start = System.nanoTime();

    assertThatThrownBy(() -> sender.send(target(receiver.url("/hook"), ""), "1", Map.of(), ""))
        .isInstanceOf(TargetUnreachableException.class);

    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(7));
  }
}
