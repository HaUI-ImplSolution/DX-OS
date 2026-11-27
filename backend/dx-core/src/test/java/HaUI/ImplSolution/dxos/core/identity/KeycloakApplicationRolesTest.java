// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.exception.KeycloakAdminException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// Applications and people: a change of an application role that fails half way must not leave the
// application with both roles or with none, and lists of people must be complete.
class KeycloakApplicationRolesTest {

  private static final String REALM = "/admin/realms/dxos";
  private static final String APP =
      "[{\"id\":\"app-uuid\",\"clientId\":\"dxos-app-shop\",\"enabled\":true,\"attributes\":"
          + "{\"dxos.app\":\"true\",\"dxos.app.model\":\"shop\",\"dxos.app.role\":\"CLERK\"}}]";
  private static final String GRANTS = REALM + "/users/sa/role-mappings/clients/model-uuid";

  @RegisterExtension
  static WireMockExtension keycloak =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  private final KeycloakAdminClient client =
      new KeycloakAdminClient(keycloak.baseUrl(), "dxos", "admin", "secret", 1000, 5000);

  @BeforeEach
  void realm() {
    keycloak.stubFor(
        post(urlEqualTo("/realms/dxos/protocol/openid-connect/token"))
            .willReturn(okJson("{\"access_token\":\"t\"}")));
    answer("/clients\\?clientId=dxos-app-.*", APP);
    answer(
        "/clients\\?clientId=dxos-m-shop",
        "[{\"id\":\"model-uuid\",\"clientId\":\"dxos-m-shop\"}]");
    answer("/clients/app-uuid/service-account-user", "{\"id\":\"sa\"}");
    answer("/clients/app-uuid/client-secret", "{\"value\":\"s3cret\"}");
    answer("/clients/model-uuid/roles/[A-Z]+", "{\"id\":\"role-id\",\"name\":\"R\"}");
    keycloak.stubFor(post(urlEqualTo(GRANTS)).willReturn(aResponse().withStatus(204)));
    keycloak.stubFor(
        post(urlEqualTo(REALM + "/clients/app-uuid/client-secret"))
            .willReturn(okJson("{\"value\":\"s3cret\"}")));
    keycloak.stubFor(delete(urlEqualTo(GRANTS)).willReturn(aResponse().withStatus(204)));
    keycloak.stubFor(
        put(urlEqualTo(REALM + "/clients/app-uuid")).willReturn(aResponse().withStatus(204)));
  }

  private static void answer(String path, String json) {
    keycloak.stubFor(get(urlMatching(REALM + path)).willReturn(okJson(json)));
  }

  @Test
  void anApplicationIsListedWithItsModelAndRole() {
    assertThat(client.appClients())
        .containsExactly(new KeycloakAdminClient.AppClient("dxos-app-shop", "shop", "CLERK", true));
    assertThat(client.rotateAppSecret("dxos-app-shop")).isEqualTo("s3cret");
  }

  @Test
  void aNewRoleIsGrantedAndTheOldOneRevoked() {
    assertThat(client.changeAppRole("dxos-app-shop", "dxos-m-shop", "BUYER").role())
        .isEqualTo("BUYER");

    keycloak.verify(1, postRequestedFor(urlEqualTo(GRANTS)));
    keycloak.verify(1, deleteRequestedFor(urlEqualTo(GRANTS)));
  }

  @Test
  void theNewRoleIsTakenBackWhenTheApplicationCannotBeChanged() {
    keycloak.stubFor(
        put(urlEqualTo(REALM + "/clients/app-uuid")).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client.changeAppRole("dxos-app-shop", "dxos-m-shop", "BUYER"))
        .isInstanceOf(KeycloakAdminException.class);

    keycloak.verify(1, deleteRequestedFor(urlEqualTo(GRANTS)));
  }

  @Test
  void theOldRoleStaysTheOnlyOneWhenItCannotBeRevoked() {
    keycloak.stubFor(delete(urlEqualTo(GRANTS)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> client.changeAppRole("dxos-app-shop", "dxos-m-shop", "BUYER"))
        .isInstanceOf(KeycloakAdminException.class);

    keycloak.verify(2, putRequestedFor(urlEqualTo(REALM + "/clients/app-uuid")));
  }

  @Test
  void anApplicationThatCannotBeGivenItsRoleIsRemoved() {
    keycloak.stubFor(post(urlEqualTo(REALM + "/clients")).willReturn(aResponse().withStatus(201)));
    keycloak.stubFor(post(urlEqualTo(GRANTS)).willReturn(aResponse().withStatus(500)));
    keycloak.stubFor(
        delete(urlEqualTo(REALM + "/clients/app-uuid")).willReturn(aResponse().withStatus(204)));

    assertThatThrownBy(
            () -> client.createAppClient("dxos-app-shop", "shop", "dxos-m-shop", "CLERK"))
        .isInstanceOf(KeycloakAdminException.class);

    keycloak.verify(1, deleteRequestedFor(urlEqualTo(REALM + "/clients/app-uuid")));
  }

  @Test
  void anotherClientIsNeverTreatedAsAnApplication() {
    answer("/clients\\?clientId=account", "[{\"id\":\"x\",\"clientId\":\"account\"}]");

    assertThatThrownBy(() -> client.deleteAppClient("account"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void peopleAreReadPageByPageAndDisabledOnesCanBeLeftOut() {
    String full =
        IntStream.range(0, 500)
            .mapToObj(i -> "{\"id\":\"u" + i + "\",\"username\":\"u" + i + "\",\"enabled\":false}")
            .collect(Collectors.joining(",", "[", "]"));
    answer("/clients/model-uuid/roles/CLERK/users\\?first=0&max=500", full);
    answer(
        "/clients/model-uuid/roles/CLERK/users\\?first=500&max=500",
        "[{\"id\":\"last\",\"firstName\":\"An\",\"lastName\":\"Binh\"}]");

    assertThat(client.usersWithClientRole("dxos-m-shop", "CLERK")).hasSize(501);
    assertThat(client.enabledUsersWithClientRole("dxos-m-shop", "CLERK"))
        .containsExactly(new KeycloakAdminClient.Person("last", "An Binh"));
  }

  @Test
  void aMissingClientRoleOrGroupMeansNobody() {
    answer("/clients\\?clientId=dxos-m-none", "[]");
    answer("/groups\\?search=NONE&exact=true", "[]");
    keycloak.stubFor(
        get(urlMatching(REALM + "/clients/model-uuid/roles/GONE/users.*"))
            .willReturn(aResponse().withStatus(404)));

    assertThat(client.usersWithClientRole("dxos-m-none", "CLERK")).isEmpty();
    assertThat(client.usersWithClientRole("dxos-m-shop", "GONE")).isEmpty();
    assertThat(client.clientRoleNames("dxos-m-none")).isEmpty();
    assertThat(client.groupMembers("NONE")).isEmpty();
    assertThat(client.groupExists("NONE")).isFalse();
    client.deleteClient("dxos-m-none");
    client.removeUserFromGroup("u1", "NONE");
    assertThatThrownBy(() -> client.addUserToGroup("u1", "NONE"))
        .isInstanceOf(KeycloakAdminException.class);
  }

  @Test
  void whatAlreadyExistsIsKept() {
    keycloak.stubFor(post(urlEqualTo(REALM + "/clients")).willReturn(aResponse().withStatus(409)));
    keycloak.stubFor(post(urlEqualTo(REALM + "/groups")).willReturn(aResponse().withStatus(409)));

    client.ensureClient("dxos-m-shop");
    client.ensureGroup("NORTH");
    client.ensureClientRole("dxos-m-shop", "CLERK", "d");
  }

  @Test
  void groupMembersAreAddedRemovedAndListed() {
    answer("/groups\\?search=NORTH&exact=true", "[{\"id\":\"g1\",\"name\":\"NORTH\"}]");
    answer("/groups/g1/members.*", "[{\"id\":\"u1\",\"username\":\"an\"}]");
    keycloak.stubFor(
        put(urlEqualTo(REALM + "/users/u1/groups/g1")).willReturn(aResponse().withStatus(204)));
    keycloak.stubFor(
        delete(urlEqualTo(REALM + "/users/u1/groups/g1")).willReturn(aResponse().withStatus(204)));

    client.addUserToGroup("u1", "NORTH");
    client.removeUserFromGroup("u1", "NORTH");

    assertThat(client.enabledGroupMembers("NORTH"))
        .extracting(KeycloakAdminClient.Person::name)
        .isEqualTo(List.of("an"));
  }

  @Test
  void accountsAreSearchedFoundAndChanged() {
    String user =
        "{\"id\":\"u1\",\"username\":\"an\",\"firstName\":\"An\",\"enabled\":true,"
            + "\"email\":\"an@example.test\"}";
    answer("/users\\?search=an&first=0&max=10", "[" + user + "]");
    answer("/users/count\\?search=an", "1");
    answer("/users/u1", user);
    answer("/users\\?username=an&exact=true", "[" + user + "]");
    answer("/users/u1/groups", "[{\"name\":\"NORTH\"}]");
    keycloak.stubFor(get(urlEqualTo(REALM + "/users/u2")).willReturn(aResponse().withStatus(404)));
    keycloak.stubFor(
        put(urlMatching(REALM + "/users/u1.*")).willReturn(aResponse().withStatus(204)));
    keycloak.stubFor(
        delete(urlEqualTo(REALM + "/users/u1")).willReturn(aResponse().withStatus(204)));

    assertThat(client.searchAccounts(" an ", 0, 10).total()).isEqualTo(1);
    assertThat(client.findAccount("u1")).isPresent();
    assertThat(client.findAccount("u2")).isEmpty();
    assertThat(client.userExists("u2")).isFalse();
    assertThat(client.userExists("u1")).isTrue();
    assertThat(client.findUserId("an")).contains("u1");
    assertThat(client.groupsOf("u1")).containsExactly("NORTH");
    client.setEnabled("u1", false);
    client.resetPassword("u1", "temporary");
    client.deleteUser("u1");
    keycloak.verify(putRequestedFor(urlEqualTo(REALM + "/users/u1/reset-password")));
  }
}
