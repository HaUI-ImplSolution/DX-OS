// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.exception.KeycloakAdminException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClientResponseException;

// The cases where a wrong answer from Keycloak would be read wrongly: GlobalExceptionHandler tells
// a refused service account from an outage by the cause of the exception, and several callers
// act on "missing" or "created".
class KeycloakAdminClientTest {

  private static final String TOKEN = "/realms/dxos/protocol/openid-connect/token";
  private static final String BEARER = "Bearer t0k";

  @RegisterExtension
  static WireMockExtension keycloak =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  private static KeycloakAdminClient client(String baseUrl, int readTimeoutMs) {
    return new KeycloakAdminClient(baseUrl, "dxos", "admin-client", "secret", 1000, readTimeoutMs);
  }

  private static KeycloakAdminClient client() {
    return client(keycloak.baseUrl(), 5000);
  }

  private static void tokenIssued() {
    keycloak.stubFor(post(urlEqualTo(TOKEN)).willReturn(okJson("{\"access_token\":\"t0k\"}")));
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403})
  void refusedServiceAccountKeepsTheStatusAsTheCause(int status) {
    keycloak.stubFor(post(urlEqualTo(TOKEN)).willReturn(aResponse().withStatus(status)));

    assertThatThrownBy(() -> client().createUser("an", "An Binh", null, "pw"))
        .isInstanceOf(KeycloakAdminException.class)
        .cause()
        .isInstanceOfSatisfying(
            RestClientResponseException.class,
            cause -> assertThat(cause.getStatusCode().value()).isEqualTo(status));
  }

  @Test
  void refusedServiceAccountStaysRefusedWhenTheTokenIsFetchedInsideALookup() {
    keycloak.stubFor(post(urlEqualTo(TOKEN)).willReturn(aResponse().withStatus(403)));

    assertThatThrownBy(() -> client().clientRolesOf("u1"))
        .isInstanceOf(KeycloakAdminException.class)
        .cause()
        .isInstanceOf(RestClientResponseException.class);
  }

  @Test
  void aStoppedKeycloakIsUnreachableAndNotRefused() throws Exception {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }

    assertThatThrownBy(
            () ->
                client("http://127.0.0.1:" + closedPort, 1000)
                    .createUser("an", "An Binh", null, "pw"))
        .isInstanceOf(KeycloakAdminException.class)
        .hasMessageContaining("unreachable")
        .cause()
        .isNotInstanceOf(RestClientResponseException.class);
  }

  @Test
  void aSlowKeycloakTimesOutInsteadOfHanging() {
    keycloak.stubFor(
        post(urlEqualTo(TOKEN))
            .willReturn(okJson("{\"access_token\":\"t0k\"}").withFixedDelay(3000)));
    long start = System.nanoTime();

    assertThatThrownBy(
            () -> client(keycloak.baseUrl(), 500).createUser("an", "An Binh", null, "pw"))
        .isInstanceOf(KeycloakAdminException.class)
        .hasMessageContaining("unreachable");

    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void aTokenReplyWithoutAnAccessTokenStopsBeforeAnyAdminCall() {
    keycloak.stubFor(post(urlEqualTo(TOKEN)).willReturn(okJson("{}")));

    assertThatThrownBy(() -> client().createUser("an", "An Binh", null, "pw"))
        .isInstanceOf(KeycloakAdminException.class)
        .hasMessageContaining("no access token");

    keycloak.verify(0, anyRequestedFor(urlPathMatching("/admin/.*")));
  }

  @Test
  void aCreatedUserIsTheIdInTheLocationHeader() {
    tokenIssued();
    keycloak.stubFor(
        post(urlEqualTo("/admin/realms/dxos/users"))
            .withHeader("Authorization", equalTo(BEARER))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader(
                        "Location", keycloak.baseUrl() + "/admin/realms/dxos/users/abc-123")));

    assertThat(client().createUser("an", "An Binh", null, "pw")).isEqualTo("abc-123");
  }

  @Test
  void aTakenUsernameIsAConflictAndNotAnOutage() {
    tokenIssued();
    keycloak.stubFor(
        post(urlEqualTo("/admin/realms/dxos/users")).willReturn(aResponse().withStatus(409)));

    assertThatThrownBy(() -> client().createUser("an", "An Binh", null, "pw"))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void aRoleIsNotCreatedWhenTheLookupItselfFailed() {
    tokenIssued();
    keycloak.stubFor(
        get(urlEqualTo("/admin/realms/dxos/roles/STAFF")).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client().ensureRealmRole("STAFF", "Staff"))
        .isInstanceOf(KeycloakAdminException.class);

    keycloak.verify(0, postRequestedFor(urlEqualTo("/admin/realms/dxos/roles")));
  }

  // Applying a model again after it stopped half way runs these steps again.
  private static Stream<Arguments> repeatableCreates() {
    return Stream.of(
        Arguments.of(
            "/admin/realms/dxos/clients",
            (Consumer<KeycloakAdminClient>) c -> c.ensureClient("dxos-m-x")),
        Arguments.of(
            "/admin/realms/dxos/groups",
            (Consumer<KeycloakAdminClient>) c -> c.ensureGroup("DAO_TAO")));
  }

  @ParameterizedTest
  @MethodSource("repeatableCreates")
  void aClientOrGroupThatExistsAlreadyIsNotAnError(
      String path, Consumer<KeycloakAdminClient> step) {
    tokenIssued();
    keycloak.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(409)));

    assertThatCode(() -> step.accept(client())).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @MethodSource("repeatableCreates")
  void anyOtherRefusalWhenCreatingAClientOrGroupIsAnError(
      String path, Consumer<KeycloakAdminClient> step) {
    tokenIssued();
    keycloak.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> step.accept(client())).isInstanceOf(KeycloakAdminException.class);
  }

  @Test
  void deletingAClientThatIsGoneIsNotAnErrorButAFailedDeleteIs() {
    tokenIssued();
    keycloak.stubFor(
        get(urlEqualTo("/admin/realms/dxos/clients?clientId=dxos-m-gone"))
            .willReturn(okJson("[]")));
    keycloak.stubFor(
        get(urlEqualTo("/admin/realms/dxos/clients?clientId=dxos-m-x"))
            .willReturn(okJson("[{\"id\":\"u1\",\"clientId\":\"dxos-m-x\"}]")));
    keycloak.stubFor(
        delete(urlEqualTo("/admin/realms/dxos/clients/u1"))
            .willReturn(aResponse().withStatus(500)));

    assertThatCode(() -> client().deleteClient("dxos-m-gone")).doesNotThrowAnyException();
    keycloak.verify(0, deleteRequestedFor(urlEqualTo("/admin/realms/dxos/clients/u1")));
    assertThatThrownBy(() -> client().deleteClient("dxos-m-x"))
        .isInstanceOf(KeycloakAdminException.class);
  }
}
