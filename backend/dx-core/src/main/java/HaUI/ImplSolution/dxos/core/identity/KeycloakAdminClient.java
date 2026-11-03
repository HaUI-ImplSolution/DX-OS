// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.exception.KeycloakAdminException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// Calls the Keycloak Admin REST API with a dedicated service account (client credentials) that
// only holds the realm-management roles needed to manage users.
@Component
public class KeycloakAdminClient {

  // A user as other users may see them.
  public record Person(String id, String name) {}

  private static final int MAX_PEOPLE = 500;

  private final RestClient restClient;
  private final String realm;
  private final String clientId;
  private final String clientSecret;

  public KeycloakAdminClient(@Value("${keycloak.admin.base-url}") String baseUrl,
      @Value("${keycloak.admin.realm}") String realm,
      @Value("${keycloak.admin.client-id}") String clientId,
      @Value("${keycloak.admin.client-secret}") String clientSecret,
      @Value("${keycloak.admin.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${keycloak.admin.read-timeout-ms:10000}") int readTimeoutMs) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    this.realm = realm;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  // Creates an enabled user with a temporary password (changed at first login) and the given
  // realm role. Returns the new user id (the token "sub"). A half-created user is removed again.
  public String createUser(String username, String fullName, String email, String temporaryPassword,
      String realmRole) {
    String token = accessToken();
    String userId = createAccount(token, username, fullName, email, temporaryPassword);
    try {
      assignRealmRole(token, userId, realmRole);
    } catch (RuntimeException e) {
      deleteUserQuietly(token, userId);
      throw e;
    }
    return userId;
  }

  // Creates the realm role when it does not exist yet; an existing role is left as it is.
  public void ensureRealmRole(String name, String description) {
    String token = accessToken();
    try {
      restClient.get().uri("/admin/realms/{realm}/roles/{name}", realm, name)
          .headers(h -> h.setBearerAuth(token)).retrieve().toBodilessEntity();
      return;
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() != 404) {
        throw new KeycloakAdminException(
            "Keycloak refused a role lookup: HTTP " + e.getStatusCode().value(), e);
      }
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
    try {
      restClient.post().uri("/admin/realms/{realm}/roles", realm)
          .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("name", name, "description", description)).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() != 409) {
        throw new KeycloakAdminException(
            "Keycloak refused to create a role: HTTP " + e.getStatusCode().value(), e);
      }
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  // Creates the group when it does not exist yet; an existing group is left as it is.
  public void ensureGroup(String name) {
    try {
      restClient.post().uri("/admin/realms/{realm}/groups", realm)
          .headers(h -> h.setBearerAuth(accessToken())).contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("name", name)).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() != 409) {
        throw new KeycloakAdminException(
            "Keycloak refused to create a group: HTTP " + e.getStatusCode().value(), e);
      }
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  // Puts the user in the group with exactly this name; being in it already changes nothing.
  public void addUserToGroup(String userId, String group) {
    String groupId = findGroupId(group)
        .orElseThrow(() -> new KeycloakAdminException("Keycloak has no group " + group));
    try {
      restClient.put()
          .uri("/admin/realms/{realm}/users/{user}/groups/{group}", realm, userId, groupId)
          .headers(h -> h.setBearerAuth(accessToken())).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw new KeycloakAdminException(
          "Keycloak refused to add a user to a group: HTTP " + e.getStatusCode().value(), e);
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  // Takes the user out of the group with exactly this name; being out of it already, or a group
  // that does not exist, changes nothing.
  public void removeUserFromGroup(String userId, String group) {
    String groupId = findGroupId(group).orElse(null);
    if (groupId == null) {
      return;
    }
    try {
      restClient.delete()
          .uri("/admin/realms/{realm}/users/{user}/groups/{group}", realm, userId, groupId)
          .headers(h -> h.setBearerAuth(accessToken())).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw new KeycloakAdminException(
          "Keycloak refused to remove a user from a group: HTTP " + e.getStatusCode().value(), e);
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  private java.util.Optional<String> findGroupId(String group) {
    return list("/admin/realms/{realm}/groups?search={name}&exact=true", group).stream()
        .filter(found -> group.equals(found.get("name"))).findFirst()
        .map(found -> found.get("id").toString());
  }

  public List<Person> usersWithRole(String role) {
    return people("/admin/realms/{realm}/roles/{name}/users?max=" + MAX_PEOPLE, role);
  }

  // The members of the group with exactly this name; none when there is no such group.
  public List<Person> groupMembers(String group) {
    return list("/admin/realms/{realm}/groups?search={name}&exact=true", group).stream()
        .filter(found -> group.equals(found.get("name"))).findFirst()
        .map(found -> people("/admin/realms/{realm}/groups/{name}/members?max=" + MAX_PEOPLE,
            found.get("id").toString()))
        .orElse(List.of());
  }

  private List<Person> people(String uri, String variable) {
    return list(uri, variable).stream().map(KeycloakAdminClient::person).toList();
  }

  private static Person person(Map<String, Object> user) {
    String name =
        (user.getOrDefault("firstName", "") + " " + user.getOrDefault("lastName", "")).trim();
    return new Person(user.get("id").toString(),
        name.isEmpty() ? String.valueOf(user.get("username")) : name);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> list(String uri, String variable) {
    try {
      List<Map<String, Object>> rows = restClient.get().uri(uri, realm, variable)
          .headers(h -> h.setBearerAuth(accessToken())).retrieve().body(List.class);
      return rows == null ? List.of() : rows;
    } catch (RestClientResponseException e) {
      throw new KeycloakAdminException(
          "Keycloak refused a lookup: HTTP " + e.getStatusCode().value(), e);
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  public void deleteUser(String userId) {
    try {
      restClient.delete().uri("/admin/realms/{realm}/users/{id}", realm, userId)
          .headers(h -> h.setBearerAuth(accessToken())).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw new KeycloakAdminException(
          "Keycloak refused to delete a user: HTTP " + e.getStatusCode().value(), e);
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  // Id of the user with exactly this username, if there is one.
  @SuppressWarnings("unchecked")
  public java.util.Optional<String> findUserId(String username) {
    try {
      java.util.List<Map<String, Object>> users = restClient.get()
          .uri("/admin/realms/{realm}/users?username={username}&exact=true", realm, username)
          .headers(h -> h.setBearerAuth(accessToken())).retrieve().body(java.util.List.class);
      return users == null || users.isEmpty() ? java.util.Optional.empty()
          : java.util.Optional.of(users.get(0).get("id").toString());
    } catch (RestClientResponseException e) {
      throw new KeycloakAdminException(
          "Keycloak refused a user lookup: HTTP " + e.getStatusCode().value(), e);
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  public boolean userExists(String userId) {
    try {
      restClient.get().uri("/admin/realms/{realm}/users/{id}", realm, userId)
          .headers(h -> h.setBearerAuth(accessToken())).retrieve().toBodilessEntity();
      return true;
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404) {
        return false;
      }
      throw new KeycloakAdminException(
          "Keycloak refused a user lookup: HTTP " + e.getStatusCode().value(), e);
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  @SuppressWarnings("unchecked")
  private String accessToken() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    try {
      Map<String, Object> response = restClient.post()
          .uri("/realms/{realm}/protocol/openid-connect/token", realm)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
      Object token = response == null ? null : response.get("access_token");
      if (token == null) {
        throw new KeycloakAdminException("Keycloak returned no access token");
      }
      return token.toString();
    } catch (RestClientResponseException e) {
      throw new KeycloakAdminException(
          "Keycloak refused the admin client credentials: HTTP " + e.getStatusCode().value(), e);
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  private String createAccount(String token, String username, String fullName, String email,
      String temporaryPassword) {
    // Keycloak builds the token "name" claim as "firstName lastName", so the first word goes to
    // firstName and the rest to lastName to reproduce fullName exactly.
    String[] words = fullName.trim().split("\\s+", 2);
    Map<String, Object> user = new java.util.LinkedHashMap<>();
    user.put("username", username);
    user.put("enabled", true);
    user.put("firstName", words[0]);
    user.put("lastName", words.length > 1 ? words[1] : "");
    if (email != null && !email.isBlank()) {
      user.put("email", email);
    }
    user.put("credentials",
        List.of(Map.of("type", "password", "value", temporaryPassword, "temporary", true)));
    try {
      var response = restClient.post().uri("/admin/realms/{realm}/users", realm)
          .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(user)
          .retrieve().toBodilessEntity();
      URI location = response.getHeaders().getLocation();
      if (location == null) {
        throw new KeycloakAdminException("Keycloak did not return the new user location");
      }
      String path = location.getPath();
      return path.substring(path.lastIndexOf('/') + 1);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 409) {
        throw new InvalidStateException("Username or email already exists: " + username);
      }
      throw new KeycloakAdminException(
          "Keycloak refused to create a user: HTTP " + e.getStatusCode().value(), e);
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  @SuppressWarnings("unchecked")
  private void assignRealmRole(String token, String userId, String roleName) {
    try {
      Map<String, Object> role =
          restClient.get().uri("/admin/realms/{realm}/roles/{name}", realm, roleName)
              .headers(h -> h.setBearerAuth(token)).retrieve().body(Map.class);
      restClient.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
          .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
          .body(List.of(role)).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw new KeycloakAdminException(
          "Keycloak refused to assign the " + roleName + " role: HTTP " + e.getStatusCode().value(),
          e);
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  private void deleteUserQuietly(String token, String userId) {
    try {
      restClient.delete().uri("/admin/realms/{realm}/users/{id}", realm, userId)
          .headers(h -> h.setBearerAuth(token)).retrieve().toBodilessEntity();
    } catch (RuntimeException ignored) {
      // Best effort; the caller is already failing with the original error.
    }
  }
}
