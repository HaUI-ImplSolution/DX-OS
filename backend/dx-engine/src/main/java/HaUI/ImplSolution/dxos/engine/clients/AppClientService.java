// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
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
import HaUI.ImplSolution.dxos.engine.projects.ProjectLinks;
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

  // project is null while the application belongs to no project.
  public record AppView(
      String name, String clientId, String model, String role, boolean enabled, String project) {}

  public record NewApp(String name, String model, String role, String project) {}

  public record CreatedApp(
      String name, String clientId, String model, String role, String project, String secret) {}

  public record RenewedApp(String name, String clientId, String secret) {}

  public record ChangedRole(String role) {}

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]{0,39}");
  private static final Logger log = LoggerFactory.getLogger(AppClientService.class);

  private final KeycloakAdminClient keycloak;
  private final ModelRegistry registry;
  private final ProjectLinks projects;
  private final UserContext user;
  private final AuditRecorder audit;
  private final AdminEventPublisher events;

  public AppClientService(
      KeycloakAdminClient keycloak,
      ModelRegistry registry,
      ProjectLinks projects,
      UserContext user,
      AuditRecorder audit,
      AdminEventPublisher events) {
    this.keycloak = keycloak;
    this.registry = registry;
    this.projects = projects;
    this.user = user;
    this.audit = audit;
    this.events = events;
  }

  public List<AppView> list() {
    Map<String, String> projectOf = projects.ofClients();
    return keycloak.appClients().stream().map(app -> view(app, projectOf)).toList();
  }

  public AppView get(String name) {
    AppClient app =
        keycloak
            .findAppClient(clientId(name))
            .orElseThrow(() -> new ResourceNotFoundException("Application " + name + " not found"));
    return view(app, projects.ofClients());
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
    Model running =
        registry
            .find(request.model())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Model " + request.model() + " is not applied"));
    if (running.roles().stream().noneMatch(declared -> declared.name().equals(request.role()))) {
      throw new ResourceNotFoundException(
          "Model " + request.model() + " has no role " + request.role());
    }
    String project =
        request.project() == null || request.project().isBlank() ? null : request.project();
    if (project != null && !projects.exists(project)) {
      throw new ResourceNotFoundException("Project " + project + " not found");
    }
    String clientId = KeycloakAdminClient.APP_CLIENT_PREFIX + request.name();
    String secret =
        keycloak.createAppClient(
            clientId, request.model(), ModelClient.of(request.model()), request.role());
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("model", request.model());
    facts.put("role", request.role());
    if (project != null) {
      projects.setClient(clientId, project);
      facts.put("project", project);
    }
    audited("CLIENT_CREATED", clientId, facts);
    return new CreatedApp(
        request.name(), clientId, request.model(), request.role(), project, secret);
  }

  public RenewedApp renewSecret(String name) {
    String clientId = clientId(name);
    String secret = keycloak.rotateAppSecret(clientId);
    audited("CLIENT_SECRET_ROTATED", clientId, Map.of());
    return new RenewedApp(name, clientId, secret);
  }

  public AppView changeRole(String name, ChangedRole request) {
    if (request == null || request.role() == null || request.role().isBlank()) {
      throw new ModelValidationException(Map.of("role", "is required"));
    }
    String clientId = clientId(name);
    AppClient app =
        keycloak
            .findAppClient(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Application " + name + " not found"));
    Model running =
        registry
            .find(app.model())
            .orElseThrow(
                () -> new ResourceNotFoundException("Model " + app.model() + " is not applied"));
    if (running.roles().stream().noneMatch(declared -> declared.name().equals(request.role()))) {
      throw new ResourceNotFoundException(
          "Model " + app.model() + " has no role " + request.role());
    }
    AppClient changed =
        keycloak.changeAppRole(clientId, ModelClient.of(app.model()), request.role());
    if (!app.role().equals(changed.role())) {
      audited(
          "CLIENT_ROLE_CHANGED",
          clientId,
          Map.of("model", app.model(), "from", app.role(), "to", changed.role()));
    }
    return view(changed, projects.ofClients());
  }

  public void remove(String name) {
    String clientId = clientId(name);
    AppClient app =
        keycloak
            .findAppClient(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Application " + name + " not found"));
    keycloak.deleteAppClient(clientId);
    projects.dropClient(clientId);
    audited("CLIENT_DELETED", clientId, Map.of("model", app.model(), "role", app.role()));
  }

  private static String clientId(String name) {
    if (!NAME.matcher(name).matches()) {
      throw new ResourceNotFoundException("Application " + name + " not found");
    }
    return KeycloakAdminClient.APP_CLIENT_PREFIX + name;
  }

  private static AppView view(AppClient app, Map<String, String> projectOf) {
    return new AppView(
        app.clientId().substring(KeycloakAdminClient.APP_CLIENT_PREFIX.length()),
        app.clientId(),
        app.model(),
        app.role(),
        app.enabled(),
        projectOf.get(app.clientId()));
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
