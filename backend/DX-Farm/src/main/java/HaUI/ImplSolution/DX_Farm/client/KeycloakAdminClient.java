package HaUI.ImplSolution.DX_Farm.client;

import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.KeycloakAdminException;
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

  private static final String FARMER_ROLE = "FARMER";

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

  // Creates an enabled user with a temporary password (changed at first login) and the FARMER
  // realm role. Returns the new user id (the token "sub"). A half-created user is removed again.
  public String createFarmerUser(String username, String fullName, String email,
      String temporaryPassword) {
    String token = accessToken();
    String userId = createUser(token, username, fullName, email, temporaryPassword);
    try {
      assignFarmerRole(token, userId);
    } catch (RuntimeException e) {
      deleteUserQuietly(token, userId);
      throw e;
    }
    return userId;
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

  private String createUser(String token, String username, String fullName, String email,
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
  private void assignFarmerRole(String token, String userId) {
    try {
      Map<String, Object> role =
          restClient.get().uri("/admin/realms/{realm}/roles/{name}", realm, FARMER_ROLE)
              .headers(h -> h.setBearerAuth(token)).retrieve().body(Map.class);
      restClient.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
          .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
          .body(List.of(role)).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw new KeycloakAdminException(
          "Keycloak refused to assign the FARMER role: HTTP " + e.getStatusCode().value(), e);
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
