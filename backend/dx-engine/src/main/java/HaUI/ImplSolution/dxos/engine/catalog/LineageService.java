// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.catalog;

import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import HaUI.ImplSolution.dxos.model.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

// Where the value of each field of an applied model comes from, what in the model reads it, and
// where it goes out. It is read from the model file alone: the core keeps the value a person or an
// outside application wrote and does not transform it, so there are no transformation steps.
@Service
public class LineageService {

  // kind is one of the values listed in the description of the endpoint; name and detail say which.
  public record Edge(String kind, String name, String detail) {}

  public record FieldLineage(
      String entity,
      String field,
      String label,
      String type,
      List<Edge> origins,
      List<Edge> uses,
      List<Edge> destinations) {}

  public record Lineage(String model, int version, List<FieldLineage> fields) {}

  private final CatalogService catalog;

  public LineageService(CatalogService catalog) {
    this.catalog = catalog;
  }

  public Lineage of(Model model) {
    Object version = catalog.versions(model.name()).get(0).get("version");
    return lineage(model, ((Number) version).intValue());
  }

  static Lineage lineage(Model model, int version) {
    List<FieldLineage> fields = new ArrayList<>();
    for (Entity entity : model.entities().values()) {
      for (Field field : entity.fields().values()) {
        fields.add(
            new FieldLineage(
                entity.name(),
                field.name(),
                field.label(),
                field.type().key(),
                origins(model, entity, field),
                uses(entity, field),
                destinations(model, entity, field)));
      }
    }
    return new Lineage(model.name(), version, fields);
  }

  // The roles that may write the entity, the default, and what the value is taken from.
  private static List<Edge> origins(Model model, Entity entity, Field field) {
    List<Edge> out = new ArrayList<>();
    if (field.type() == FieldType.CODE) {
      out.add(new Edge("generated", field.format(), null));
      return out;
    }
    entity
        .access()
        .forEach(
            (role, scope) -> {
              if (scope.canWrite()) {
                out.add(new Edge("input", role, scope.key()));
              }
            });
    if (field.defaultValue() != null) {
      out.add(new Edge("default", field.defaultValue(), null));
    }
    switch (field.type()) {
      case REFERENCE -> out.add(new Edge("reference", field.to(), null));
      case UNIT ->
          out.add(
              new Edge(
                  "unit",
                  "units",
                  String.join(", ", model.units().stream().map(Model.Unit::name).toList())));
      case FILE -> out.add(new Edge("file", "object store", null));
      default -> {}
    }
    if (field.renamedFrom() != null) {
      out.add(new Edge("renamed_from", field.renamedFrom(), null));
    }
    return out;
  }

  // The rules of the entity that read the field.
  private static List<Edge> uses(Entity entity, Field field) {
    List<Edge> out = new ArrayList<>();
    for (Field other : entity.fields().values()) {
      var condition = other.requiredWhen();
      if (condition != null && condition.field().equals(field.name())) {
        out.add(new Edge("required_when", other.name(), describe(condition)));
      }
    }
    if (field.name().equals(entity.ownerVia())) {
      out.add(new Edge("owner", field.to(), null));
    }
    if (field.type() == FieldType.UNIT) {
      List<String> roles = rolesWith(entity, Scope.UNIT);
      if (!roles.isEmpty()) {
        out.add(new Edge("unit_scope", String.join(", ", roles), null));
      }
    }
    if (entity.process() != null) {
      for (ProcessFamily.Action action : entity.process().definition().actions()) {
        if (action.require() != null && action.require().contains(field.name())) {
          out.add(new Edge("require", action.name(), null));
        }
        if (action.when() != null && action.when().field().equals(field.name())) {
          out.add(new Edge("when", action.name(), describe(action.when())));
        }
      }
    }
    return out;
  }

  // The history line of a change, which the event feed and the webhook carry, and the archive.
  private static List<Edge> destinations(Model model, Entity entity, Field field) {
    List<Edge> out = new ArrayList<>();
    // A file has no history line, and a code never changes after it is made.
    if (field.type() != FieldType.FILE && field.type() != FieldType.CODE) {
      out.add(new Edge("history", "updated", null));
    }
    out.add(new Edge("feed", "events", null));
    if (model.webhook() != null) {
      out.add(new Edge("webhook", model.webhook(), null));
    }
    if (entity.process() != null) {
      out.add(new Edge("archive", "dxos archive", null));
    }
    return out;
  }

  private static List<String> rolesWith(Entity entity, Scope scope) {
    return entity.access().entrySet().stream()
        .filter(entry -> entry.getValue() == scope)
        .map(Map.Entry::getKey)
        .toList();
  }

  private static String describe(ProcessFamily.Condition condition) {
    return condition.op() + " " + condition.value();
  }
}
