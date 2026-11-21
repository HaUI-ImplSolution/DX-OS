// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.projects;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.engine.clients.AppClientService;
import HaUI.ImplSolution.dxos.engine.clients.AppClientService.AppView;
import HaUI.ImplSolution.dxos.engine.event.AdminEventPublisher;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Projects name the group of models and outside applications that one integration uses, for
// browsing and for judging what a change reaches. A model and an application each belong to at
// most one project; an application may belong to a different project than the model it calls.
// A project grants no access and isolates nothing.
@Service
public class ProjectService {

  public record ProjectApp(String name, String model, String role) {}

  public record ProjectView(
      String name,
      String label,
      String description,
      List<String> models,
      List<ProjectApp> clients) {}

  public record NewProject(String name, String label, String description) {}

  public record ChangedProject(String label, String description) {}

  // project is null to leave the project.
  public record Link(String project) {}

  public record ModelLink(String model, String project) {}

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]{0,39}");
  private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

  private final JdbcTemplate jdbc;
  private final ProjectLinks links;
  private final AppClientService apps;
  private final ModelRegistry registry;
  private final UserContext user;
  private final AuditRecorder audit;
  private final AdminEventPublisher events;

  public ProjectService(
      JdbcTemplate jdbc,
      ProjectLinks links,
      AppClientService apps,
      ModelRegistry registry,
      UserContext user,
      AuditRecorder audit,
      AdminEventPublisher events) {
    this.jdbc = jdbc;
    this.links = links;
    this.apps = apps;
    this.registry = registry;
    this.user = user;
    this.audit = audit;
    this.events = events;
  }

  public List<ProjectView> list() {
    Map<String, String> models = links.ofModels();
    List<AppView> clients = apps.list();
    return jdbc
        .queryForList("SELECT name, label, description FROM engine.project ORDER BY name")
        .stream()
        .map(row -> view(row, models, clients))
        .toList();
  }

  public ProjectView get(String name) {
    return list().stream()
        .filter(project -> project.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Project " + name + " not found"));
  }

  public ProjectView create(NewProject request) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (request == null || request.name() == null || !NAME.matcher(request.name()).matches()) {
      errors.put("name", "must match [a-z][a-z0-9_-]* and be at most 40 characters");
    }
    if (!errors.isEmpty()) {
      throw new ModelValidationException(errors);
    }
    String label =
        request.label() == null || request.label().isBlank()
            ? request.name()
            : request.label().strip();
    try {
      jdbc.update(
          "INSERT INTO engine.project (name, label, description) VALUES (?, ?, ?)",
          request.name(),
          label,
          blankToNull(request.description()));
    } catch (DuplicateKeyException e) {
      throw new InvalidStateException("Project " + request.name() + " already exists");
    }
    audited(
        "PROJECT_CREATED",
        "PROJECT",
        request.name(),
        null,
        Map.of("project", request.name(), "label", label));
    return get(request.name());
  }

  public ProjectView update(String name, ChangedProject request) {
    if (request == null || request.label() == null || request.label().isBlank()) {
      throw new ModelValidationException(Map.of("label", "is required"));
    }
    int changed =
        jdbc.update(
            "UPDATE engine.project SET label = ?, description = ? WHERE name = ?",
            request.label().strip(),
            blankToNull(request.description()),
            name);
    if (changed == 0) {
      throw new ResourceNotFoundException("Project " + name + " not found");
    }
    audited(
        "PROJECT_UPDATED",
        "PROJECT",
        name,
        null,
        Map.of("project", name, "label", request.label().strip()));
    return get(name);
  }

  // A project that still holds a model or an application is not deleted; they are moved or
  // unclassified first.
  public void remove(String name) {
    ProjectView project = get(name);
    if (!project.models().isEmpty() || !project.clients().isEmpty()) {
      throw new InvalidStateException(
          "Project "
              + name
              + " still holds "
              + project.models().size()
              + " model(s) and "
              + project.clients().size()
              + " application(s)");
    }
    // What is left are links of applications that no longer exist.
    jdbc.update("DELETE FROM engine.client_project WHERE project = ?", name);
    jdbc.update("DELETE FROM engine.project WHERE name = ?", name);
    audited("PROJECT_DELETED", "PROJECT", name, null, Map.of("project", name));
  }

  public ModelLink setModelProject(String model, Link request) {
    if (registry.find(model).isEmpty()) {
      throw new ResourceNotFoundException("Model " + model + " is not applied");
    }
    String project = target(request);
    String before = links.ofModels().get(model);
    links.setModel(model, project);
    if (!java.util.Objects.equals(before, project)) {
      audited(
          "MODEL_PROJECT_CHANGED",
          "MODEL",
          model,
          model,
          Map.of("model", model, "from", nullToEmpty(before), "to", nullToEmpty(project)));
    }
    return new ModelLink(model, project);
  }

  public AppView setClientProject(String name, Link request) {
    AppView app = apps.get(name);
    String project = target(request);
    if (!java.util.Objects.equals(app.project(), project)) {
      links.setClient(app.clientId(), project);
      audited(
          "CLIENT_PROJECT_CHANGED",
          "CLIENT",
          app.clientId(),
          null,
          Map.of(
              "client", app.clientId(),
              "model", app.model(),
              "from", nullToEmpty(app.project()),
              "to", nullToEmpty(project)));
    }
    return apps.get(name);
  }

  // The project the request names, checked to exist; null when it names none.
  private String target(Link request) {
    String project = request == null ? null : blankToNull(request.project());
    if (project != null && !links.exists(project)) {
      throw new ResourceNotFoundException("Project " + project + " not found");
    }
    return project;
  }

  private static ProjectView view(
      Map<String, Object> row, Map<String, String> models, List<AppView> clients) {
    String name = (String) row.get("name");
    List<String> inModels =
        models.entrySet().stream()
            .filter(link -> name.equals(link.getValue()))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    List<ProjectApp> inClients =
        clients.stream()
            .filter(app -> name.equals(app.project()))
            .map(app -> new ProjectApp(app.name(), app.model(), app.role()))
            .toList();
    return new ProjectView(
        name, (String) row.get("label"), (String) row.get("description"), inModels, inClients);
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }

  private static String nullToEmpty(String text) {
    return text == null ? "" : text;
  }

  // Writes what the administrator changed; model names the model whose webhook target hears of it.
  private void audited(
      String action, String type, String target, String model, Map<String, Object> facts) {
    Map<String, Object> metadata = new LinkedHashMap<>(facts);
    audit.record(AuditEvent.success(action, type, target, metadata));
    try {
      events.publish(action, model, user.currentUserId(), user.currentFullName(), metadata);
    } catch (RuntimeException e) {
      log.warn("The {} message could not be queued: {}", action, e.getMessage());
    }
  }
}
