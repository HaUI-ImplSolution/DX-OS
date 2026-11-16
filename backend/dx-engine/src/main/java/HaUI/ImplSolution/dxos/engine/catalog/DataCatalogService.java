// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.catalog;

import HaUI.ImplSolution.dxos.engine.apply.Sql;
import HaUI.ImplSolution.dxos.engine.clients.AppClientService;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Describes the data of an applied model from the model itself: what each entity holds, who may
// reach it, how entities refer to each other, and where the data comes from and goes to.
@Service
public class DataCatalogService {

  public record Field(
      String name,
      String label,
      String type,
      boolean required,
      boolean unique,
      boolean multiple,
      String reference) {}

  // rows is -1 when the table cannot be read, for example while the model is being applied.
  public record EntityInfo(
      String name,
      String label,
      long rows,
      boolean hasProcess,
      String family,
      List<String> handlers,
      Map<String, String> access,
      List<Field> fields) {}

  // via is the field that holds the reference, or the owner_via of the entity.
  public record Link(String from, String via, String to, String kind) {}

  // kind is role, app, webhook, completion, feed or archive.
  public record Point(String kind, String name, String detail) {}

  public record Catalog(
      String model,
      List<Point> origins,
      List<Point> destinations,
      List<EntityInfo> entities,
      List<Link> links) {}

  private final JdbcTemplate jdbc;
  private final AppClientService apps;

  public DataCatalogService(JdbcTemplate jdbc, AppClientService apps) {
    this.jdbc = jdbc;
    this.apps = apps;
  }

  public Catalog of(Model model) {
    List<EntityInfo> entities = new ArrayList<>();
    List<Link> links = new ArrayList<>();
    for (Entity entity : model.entities().values()) {
      entities.add(describe(model, entity));
      entity.fields().values().stream()
          .filter(field -> field.type() == FieldType.REFERENCE)
          .forEach(
              field -> links.add(new Link(entity.name(), field.name(), field.to(), "reference")));
      if (entity.ownerVia() != null) {
        entity.fields().values().stream()
            .filter(field -> entity.ownerVia().equals(field.name()))
            .findFirst()
            .ifPresent(
                field -> links.add(new Link(entity.name(), field.name(), field.to(), "owner")));
      }
    }
    return new Catalog(model.name(), origins(model), destinations(model), entities, links);
  }

  private EntityInfo describe(Model model, Entity entity) {
    Map<String, String> access = new LinkedHashMap<>();
    entity
        .access()
        .forEach((role, scope) -> access.put(role, scope.name().toLowerCase(Locale.ROOT)));
    List<Field> fields =
        entity.fields().values().stream()
            .map(
                field ->
                    new Field(
                        field.name(),
                        field.label(),
                        field.type().key(),
                        field.required(),
                        field.unique(),
                        field.multiple(),
                        field.to()))
            .toList();
    var process = entity.process();
    return new EntityInfo(
        entity.name(),
        entity.label(),
        rows(model, entity),
        process != null,
        process == null ? null : process.family(),
        process == null ? List.of() : process.handlers(),
        access,
        fields);
  }

  private long rows(Model model, Entity entity) {
    try {
      Long count =
          jdbc.queryForObject("SELECT count(*) FROM " + Sql.table(model, entity), Long.class);
      return count == null ? -1 : count;
    } catch (DataAccessException e) {
      return -1;
    }
  }

  // The people who reach the model by role and the outside applications registered for it.
  private List<Point> origins(Model model) {
    List<Point> origins = new ArrayList<>();
    model.roles().forEach(role -> origins.add(new Point("role", role.name(), role.description())));
    apps.list().stream()
        .filter(app -> model.name().equals(app.model()))
        .forEach(app -> origins.add(new Point("app", app.clientId(), app.role())));
    return origins;
  }

  // The webhook target, the completion actions of the processes, the event feed and the archive.
  private List<Point> destinations(Model model) {
    List<Point> out = new ArrayList<>();
    if (model.webhook() != null) {
      out.add(new Point("webhook", model.webhook(), null));
    }
    for (Entity entity : model.entities().values()) {
      if (entity.process() == null || entity.process().after() == null) {
        continue;
      }
      entity
          .process()
          .after()
          .forEach(
              (state, completions) ->
                  completions.forEach(
                      completion ->
                          out.add(
                              new Point(
                                  "completion",
                                  completion.operation(),
                                  entity.name() + "." + state))));
    }
    out.add(new Point("feed", "events", null));
    out.add(new Point("archive", "dxos archive", null));
    return out;
  }
}
