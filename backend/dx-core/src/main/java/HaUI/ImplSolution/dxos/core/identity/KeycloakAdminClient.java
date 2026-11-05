// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
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

  // A user as the account administration shows them.
  public record Account(String id, String username, String name, String email, boolean enabled) {}

  // One page of accounts and how many match in all.
  public record Accounts(List<Account> items, long total) {}

  private static final int MAX_PEOPLE = 500;
  private static final int MAX_ROLES = 1000;

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

  // Creates an enabled user with a temporary password (changed at first login). Returns the new
  // user id (the token "sub").
  public String createUser(String username, String fullName, String email,
      String temporaryPassword) {
    return createAccount(accessToken(), username, fullName, email, temporaryPassword);
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

  // Creates the client that holds the roles of a model; it never signs anyone in. An existing
  // client is left as it is.
  public void ensureClient(String clientId) {
    try {
      restClient.post().uri("/admin/realms/{realm}/clients", realm)
          .headers(h -> h.setBearerAuth(accessToken())).contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("clientId", clientId, "protocol", "openid-connect", "bearerOnly", true,
              "enabled", true))
          .retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() != 409) {
        throw new KeycloakAdminException(
            "Keycloak refused to create a client: HTTP " + e.getStatusCode().value(), e);
      }
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  // Deletes a client with its roles, so nobody keeps them; a client that does not exist is left.
  public void deleteClient(String clientId) {
    String uuid = findClientUuid(clientId).orElse(null);
    if (uuid == null) {
      return;
    }
    try {
      restClient.delete().uri("/admin/realms/{realm}/clients/{uuid}", realm, uuid)
          .headers(h -> h.setBearerAuth(accessToken())).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() != 404) {
        throw new KeycloakAdminException(
            "Keycloak refused to delete a client: HTTP " + e.getStatusCode().value(), e);
      }
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  // Creates the role under the client when it does not exist yet; an existing role is left as it
  // is.
  public void ensureClientRole(String clientId, String name, String description) {
    String uuid = findClientUuid(clientId)
        .orElseThrow(() -> new KeycloakAdminException("Keycloak has no client " + clientId));
    String token = accessToken();
    try {
      restClient.get().uri("/admin/realms/{realm}/clients/{uuid}/roles/{name}", realm, uuid, name)
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
      restClient.post().uri("/admin/realms/{realm}/clients/{uuid}/roles", realm, uuid)
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

  // The users that hold the role of a client; nobody when the client or the role does not exist.
  public List<Person> usersWithClientRole(String clientId, String role) {
    String uuid = findClientUuid(clientId).orElse(null);
    if (uuid == null) {
      return List.of();
    }
    try {
      return people(
          "/admin/realms/{realm}/clients/" + uuid + "/roles/{name}/users?max=" + MAX_PEOPLE, role);
    } catch (KeycloakAdminException e) {
      if (e.getCause() instanceof RestClientResponseException cause
          && cause.getStatusCode().value() == 404) {
        return List.of();
      }
      throw e;
    }
  }

  // The names of the roles of a client, or empty when the client does not exist; nothing is
  // created.
  public java.util.Optional<List<String>> clientRoleNames(String clientId) {
    String uuid = findClientUuid(clientId).orElse(null);
    if (uuid == null) {
      return java.util.Optional.empty();
    }
    try {
      return java.util.Optional
          .of(rows("/admin/realms/{realm}/clients/{uuid}/roles?max=" + MAX_ROLES, uuid).stream()
              .map(role -> role.get("name").toString()).toList());
    } catch (KeycloakAdminException e) {
      if (e.getCause() instanceof RestClientResponseException cause
          && cause.getStatusCode().value() == 404) {
        return java.util.Optional.empty();
      }
      throw e;
    }
  }

  private java.util.Optional<String> findClientUuid(String clientId) {
    return list("/admin/realms/{realm}/clients?clientId={name}", clientId).stream()
        .filter(found -> clientId.equals(found.get("clientId"))).findFirst()
        .map(found -> found.get("id").toString());
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

  // Whether a group with exactly this name exists; nothing is created.
  public boolean groupExists(String name) {
    return findGroupId(name).isPresent();
  }

  private java.util.Optional<String> findGroupId(String group) {
    return list("/admin/realms/{realm}/groups?search={name}&exact=true", group).stream()
        .filter(found -> group.equals(found.get("name"))).findFirst()
        .map(found -> found.get("id").toString());
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

  private List<Map<String, Object>> list(String uri, String variable) {
    return rows(uri, variable);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(String uri, Object... variables) {
    Object[] all = new Object[variables.length + 1];
    all[0] = realm;
    System.arraycopy(variables, 0, all, 1, variables.length);
    try {
      List<Map<String, Object>> rows = restClient.get().uri(uri, all)
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

  // A page of the users whose name, username or email contains the text (all users when blank).
  public Accounts searchAccounts(String search, int first, int max) {
    String text = search == null ? "" : search.trim();
    List<Account> items =
        rows("/admin/realms/{realm}/users?search={text}&first={first}&max={max}", text, first, max)
            .stream().map(KeycloakAdminClient::account).toList();
    return new Accounts(items, countAccounts(text));
  }

  private long countAccounts(String text) {
    try {
      Integer count =
          restClient.get().uri("/admin/realms/{realm}/users/count?search={text}", realm, text)
              .headers(h -> h.setBearerAuth(accessToken())).retrieve().body(Integer.class);
      return count == null ? 0 : count;
    } catch (RestClientResponseException e) {
      throw new KeycloakAdminException(
          "Keycloak refused a lookup: HTTP " + e.getStatusCode().value(), e);
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  @SuppressWarnings("unchecked")
  public java.util.Optional<Account> findAccount(String userId) {
    try {
      Map<String, Object> user =
          restClient.get().uri("/admin/realms/{realm}/users/{id}", realm, userId)
              .headers(h -> h.setBearerAuth(accessToken())).retrieve().body(Map.class);
      return user == null ? java.util.Optional.empty() : java.util.Optional.of(account(user));
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404) {
        return java.util.Optional.empty();
      }
      throw new KeycloakAdminException(
          "Keycloak refused a user lookup: HTTP " + e.getStatusCode().value(), e);
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  // The roles the user holds in each client, by client id.
  @SuppressWarnings("unchecked")
  public Map<String, List<String>> clientRolesOf(String userId) {
    try {
      Map<String, Object> mappings =
          restClient.get().uri("/admin/realms/{realm}/users/{id}/role-mappings", realm, userId)
              .headers(h -> h.setBearerAuth(accessToken())).retrieve().body(Map.class);
      Map<String, Object> clients =
          mappings == null ? null : (Map<String, Object>) mappings.get("clientMappings");
      Map<String, List<String>> roles = new java.util.TreeMap<>();
      if (clients != null) {
        clients.forEach((client, entry) -> roles.put(client,
            ((List<Map<String, Object>>) ((Map<String, Object>) entry).get("mappings")).stream()
                .map(role -> role.get("name").toString()).sorted().toList()));
      }
      return roles;
    } catch (RestClientResponseException e) {
      throw new KeycloakAdminException(
          "Keycloak refused a lookup: HTTP " + e.getStatusCode().value(), e);
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  // The names of the groups the user is a member of.
  public List<String> groupsOf(String userId) {
    return list("/admin/realms/{realm}/users/{name}/groups", userId).stream()
        .map(group -> group.get("name").toString()).sorted().toList();
  }

  // Gives the user a role of a client; holding it already changes nothing.
  public void grantClientRole(String userId, String clientId, String role) {
    changeClientRole(userId, clientId, role, true);
  }

  // Takes the role of a client from the user; not holding it changes nothing.
  public void revokeClientRole(String userId, String clientId, String role) {
    changeClientRole(userId, clientId, role, false);
  }

  @SuppressWarnings("unchecked")
  private void changeClientRole(String userId, String clientId, String role, boolean grant) {
    String uuid = findClientUuid(clientId)
        .orElseThrow(() -> new ResourceNotFoundException("Keycloak has no client " + clientId));
    String token = accessToken();
    try {
      Map<String, Object> representation = restClient.get()
          .uri("/admin/realms/{realm}/clients/{uuid}/roles/{name}", realm, uuid, role)
          .headers(h -> h.setBearerAuth(token)).retrieve().body(Map.class);
      var request =
          grant ? restClient.post() : restClient.method(org.springframework.http.HttpMethod.DELETE);
      request
          .uri("/admin/realms/{realm}/users/{id}/role-mappings/clients/{uuid}", realm, userId, uuid)
          .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
          .body(List.of(representation)).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404) {
        throw new ResourceNotFoundException("Role " + role + " of " + clientId + " not found");
      }
      throw new KeycloakAdminException(
          "Keycloak refused to change a role: HTTP " + e.getStatusCode().value(), e);
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  // Locks the account out, or lets it sign in again.
  @SuppressWarnings("unchecked")
  public void setEnabled(String userId, boolean enabled) {
    String token = accessToken();
    try {
      Map<String, Object> user =
          restClient.get().uri("/admin/realms/{realm}/users/{id}", realm, userId)
              .headers(h -> h.setBearerAuth(token)).retrieve().body(Map.class);
      user.put("enabled", enabled);
      restClient.put().uri("/admin/realms/{realm}/users/{id}", realm, userId)
          .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON).body(user)
          .retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404) {
        throw new ResourceNotFoundException("User " + userId + " not found");
      }
      throw new KeycloakAdminException(
          "Keycloak refused to change a user: HTTP " + e.getStatusCode().value(), e);
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  // Sets a password the user must change at the next sign-in.
  public void resetPassword(String userId, String temporaryPassword) {
    try {
      restClient.put().uri("/admin/realms/{realm}/users/{id}/reset-password", realm, userId)
          .headers(h -> h.setBearerAuth(accessToken())).contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("type", "password", "value", temporaryPassword, "temporary", true))
          .retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404) {
        throw new ResourceNotFoundException("User " + userId + " not found");
      }
      throw new KeycloakAdminException(
          "Keycloak refused to reset a password: HTTP " + e.getStatusCode().value(), e);
    } catch (KeycloakAdminException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new KeycloakAdminException("Keycloak is unreachable", e);
    }
  }

  private static Account account(Map<String, Object> user) {
    Person person = person(user);
    return new Account(person.id(), String.valueOf(user.get("username")), person.name(),
        user.get("email") == null ? "" : user.get("email").toString(),
        !Boolean.FALSE.equals(user.get("enabled")));
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
}
