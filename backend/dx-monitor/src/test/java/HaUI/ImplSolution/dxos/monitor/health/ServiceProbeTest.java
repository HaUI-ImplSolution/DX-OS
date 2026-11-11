// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.health;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties.Probe;
import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties.Probe.Target;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// The probe decides whether a service is up, so a wrong answer is a false alarm or a missed outage,
// and a probe that never returns stops the whole sampler.
class ServiceProbeTest {

  private static final int TIMEOUT_MS = 500;

  @RegisterExtension
  static WireMockExtension service = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort().http2PlainDisabled(true)).build();

  private final ServiceProbe probe = new ServiceProbe(null,
      new MonitorProperties(null, null, new Probe(3, TIMEOUT_MS, List.of()), null, 0, null, null));

  private ServiceProbe.Result probe(String path, String bodyContains) {
    return probe.probe(new Target("svc", service.url(path), bodyContains, true));
  }

  @Test
  void aServiceIsUpOnlyWhenItsAnswerHasTheExpectedMarker() {
    service.stubFor(get(urlEqualTo("/good"))
        .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"UP\"}")));
    service.stubFor(get(urlEqualTo("/bad"))
        .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"DOWN\"}")));

    assertThat(probe("/good", "UP").up()).isTrue();
    assertThat(probe("/bad", "UP").up()).isFalse();
  }

  @Test
  void aServerErrorIsDown() {
    service.stubFor(get(urlEqualTo("/err")).willReturn(aResponse().withStatus(500)));

    assertThat(probe("/err", null)).isEqualTo(new ServiceProbe.Result(false, "HTTP 500"));
  }

  @Test
  void aServiceThatAnswersTooLateIsDownAtOnce() {
    service.stubFor(
        get(urlEqualTo("/late")).willReturn(aResponse().withStatus(200).withFixedDelay(3000)));
    long start = System.nanoTime();

    assertThat(probe("/late", null).up()).isFalse();

    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void aServiceThatSendsTheHeadersAndThenStallsDoesNotHoldTheProbe() {
    service.stubFor(get(urlEqualTo("/stall")).willReturn(
        aResponse().withStatus(200).withBody("x".repeat(1000)).withChunkedDribbleDelay(10, 8000)));
    long start = System.nanoTime();

    assertThat(probe("/stall", "x").up()).isFalse();

    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
  }
}
