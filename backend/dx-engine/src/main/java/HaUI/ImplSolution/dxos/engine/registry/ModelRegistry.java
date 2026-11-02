// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.registry;

import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelParser;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// The models in force: the latest applied version of each, kept in memory and refreshed after
// every apply.
@Component
@DependsOn("engineFlyway")
public class ModelRegistry {

  private final JdbcTemplate jdbc;
  private volatile Map<String, Model> models = Map.of();

  public ModelRegistry(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void reload() {
    List<String> contents = jdbc.queryForList(
        "SELECT DISTINCT ON (model) content FROM engine.model_version ORDER BY model, version DESC",
        String.class);
    Map<String, Model> loaded = new LinkedHashMap<>();
    for (String content : contents) {
      Model model = ModelParser.parse(content);
      loaded.put(model.name(), model);
    }
    models = Map.copyOf(loaded);
  }

  public Optional<Model> find(String name) {
    return Optional.ofNullable(models.get(name));
  }

  public Model require(String name) {
    return find(name)
        .orElseThrow(() -> new ResourceNotFoundException("Model " + name + " is not applied"));
  }

  public Collection<Model> all() {
    return models.values().stream().sorted((a, b) -> a.name().compareTo(b.name())).toList();
  }
}
