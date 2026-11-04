// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.registry;

import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelException;
import HaUI.ImplSolution.dxos.model.ModelParser;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// The models in force: the latest applied version of each, kept in memory and refreshed after
// every apply. A stored model that no longer passes the checks is left out and logged, so the
// others keep running.
@Component
@DependsOn("engineFlyway")
public class ModelRegistry {

  private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

  private final JdbcTemplate jdbc;
  private volatile Map<String, Model> models = Map.of();
  private volatile Set<String> maintenance = Set.of();

  public ModelRegistry(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public synchronized void reload() {
    maintenance =
        Set.copyOf(jdbc.queryForList("SELECT model FROM (SELECT DISTINCT ON (model) model, status "
            + "FROM engine.apply_run ORDER BY model, id DESC) latest "
            + "WHERE status IN ('running', 'failed')", String.class));
    List<String> contents = jdbc.queryForList(
        "SELECT DISTINCT ON (model) content FROM engine.model_version ORDER BY model, version DESC",
        String.class);
    Map<String, Model> loaded = new LinkedHashMap<>();
    for (String content : contents) {
      try {
        Model model = ModelParser.parse(content);
        loaded.put(model.name(), model);
      } catch (ModelException e) {
        log.error("A stored model is left out because it fails the checks: {}", e.problems());
      }
    }
    models = Map.copyOf(loaded);
    logSharedRoles(loaded.values());
  }

  // The apply refuses a role another model uses, but models stored before that check can still
  // share one, and the people holding it then reach the data of both.
  private static void logSharedRoles(Collection<Model> all) {
    Map<String, List<String>> owners = new LinkedHashMap<>();
    for (Model model : all) {
      model.roles().forEach(role -> owners
          .computeIfAbsent(role.name(), name -> new java.util.ArrayList<>()).add(model.name()));
    }
    owners.forEach((role, names) -> {
      if (names.size() > 1) {
        log.error("Role {} is declared by the models {}; its holders reach the data of all of them",
            role, names);
      }
    });
  }

  public synchronized void beginApply(String model) {
    java.util.LinkedHashSet<String> next = new java.util.LinkedHashSet<>(maintenance);
    next.add(model);
    maintenance = Set.copyOf(next);
  }

  public synchronized void endApply(String model) {
    java.util.LinkedHashSet<String> next = new java.util.LinkedHashSet<>(maintenance);
    next.remove(model);
    maintenance = Set.copyOf(next);
  }

  // A model is held while an apply of it runs and after one stopped before it finished.
  public boolean isHeld(String name) {
    return maintenance.contains(name);
  }

  public Optional<Model> find(String name) {
    return Optional.ofNullable(models.get(name));
  }

  public Model require(String name) {
    if (maintenance.contains(name)) {
      throw new InvalidStateException(
          "Model " + name + " is being applied or needs reconciliation");
    }
    return find(name)
        .orElseThrow(() -> new ResourceNotFoundException("Model " + name + " is not applied"));
  }

  public Collection<Model> all() {
    return models.values().stream().filter(model -> !maintenance.contains(model.name()))
        .sorted((a, b) -> a.name().compareTo(b.name())).toList();
  }
}
