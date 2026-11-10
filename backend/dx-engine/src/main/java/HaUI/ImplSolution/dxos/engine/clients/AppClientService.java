// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.clients;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient.AppClient;
import HaUI.ImplSolution.dxos.engine.event.AdminEventPublisher;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.engine.registry.ModelClient;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// The outside applications that call the API with a secret of their own. An application holds one
// role of one model, so it reaches what that role reaches and nothing else; the roles of the
// platform are not reachable from here. The secret is shown when it is made and never kept.
@Service
public class AppClientService {

  public record AppView(String name, String clientId, String model, String role, boolean enabled) {}

  public record NewApp(String name, String model, String role) {}

  public record CreatedApp(String name, String clientId, String model, String role,
      String secret) {}

  public record RenewedApp(String name, String clientId, String secret) {}

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]{0,39}");
  private static final Logger log = LoggerFactory.getLogger(AppClientService.class);

  private final KeycloakAdminClient keycloak;
  private final ModelRegistry registry;
  private final UserContext user;
  private final AuditRecorder audit;
  private final AdminEventPublisher events;

  public AppClientService(KeycloakAdminClient keycloak, ModelRegistry registry, UserContext user,
      AuditRecorder audit, AdminEventPublisher events) {
    this.keycloak = keycloak;
    this.registry = registry;
    this.user = user;
    this.audit = audit;
    this.events = events;
  }

  public List<AppView> list() {
    return keycloak.appClients().stream().map(AppClientService::view).toList();
  }

  public CreatedApp create(NewApp request) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (request == null || request.name() == null || !NAME.matcher(request.name()).matches()) {
      errors.put("name", "must match [a-z][a-z0-9_-]* and be at most 40 characters");
    }
    if (request == null || request.model() == null || request.model().isBlank()) {
      errors.put("model", "is required");
    }
    if (request == null || request.role() == null || request.role().isBlank()) {
      errors.put("role", "is required");
    }
    if (!errors.isEmpty()) {
      throw new ModelValidationException(errors);
    }
    Model running = registry.find(request.model()).orElseThrow(
        () -> new ResourceNotFoundException("Model " + request.model() + " is not applied"));
    if (running.roles().stream().noneMatch(declared -> declared.name().equals(request.role()))) {
      throw new ResourceNotFoundException(
          "Model " + request.model() + " has no role " + request.role());
    }
    String clientId = KeycloakAdminClient.APP_CLIENT_PREFIX + request.name();
    String secret = keycloak.createAppClient(clientId, request.model(),
        ModelClient.of(request.model()), request.role());
    audited("CLIENT_CREATED", clientId, Map.of("model", request.model(), "role", request.role()));
    return new CreatedApp(request.name(), clientId, request.model(), request.role(), secret);
  }

  public RenewedApp renewSecret(String name) {
    String clientId = clientId(name);
    String secret = keycloak.rotateAppSecret(clientId);
    audited("CLIENT_SECRET_ROTATED", clientId, Map.of());
    return new RenewedApp(name, clientId, secret);
  }

  public void remove(String name) {
    String clientId = clientId(name);
    AppClient app = keycloak.findAppClient(clientId)
        .orElseThrow(() -> new ResourceNotFoundException("Application " + name + " not found"));
    keycloak.deleteAppClient(clientId);
    audited("CLIENT_DELETED", clientId, Map.of("model", app.model(), "role", app.role()));
  }

  private static String clientId(String name) {
    if (!NAME.matcher(name).matches()) {
      throw new ResourceNotFoundException("Application " + name + " not found");
    }
    return KeycloakAdminClient.APP_CLIENT_PREFIX + name;
  }

  private static AppView view(AppClient app) {
    return new AppView(app.clientId().substring(KeycloakAdminClient.APP_CLIENT_PREFIX.length()),
        app.clientId(), app.model(), app.role(), app.enabled());
  }

  // Writes what the administrator changed; the facts name the application, never its secret.
  private void audited(String action, String clientId, Map<String, Object> facts) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("client", clientId);
    metadata.putAll(facts);
    audit.record(AuditEvent.success(action, "CLIENT", clientId, metadata));
    try {
      events.publish(action, null, user.currentUserId(), user.currentFullName(), metadata);
    } catch (RuntimeException e) {
      log.warn("The {} message could not be queued: {}", action, e.getMessage());
    }
  }
}
