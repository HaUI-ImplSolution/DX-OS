// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.meta;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.engine.data.DataService;
import HaUI.ImplSolution.dxos.engine.data.DataService.Access;
import HaUI.ImplSolution.dxos.engine.process.ProcessInfo;
import HaUI.ImplSolution.dxos.engine.registry.ModelClient;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import HaUI.ImplSolution.dxos.model.ProcessFamily.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta/models")
@Tag(name = "Metadata", description = "What the applied models contain, as seen by the caller")
public class MetaController {

  // multiple, min, max, pattern and requiredWhen are null (multiple false) when the field does not
  // declare them; min and max are text, a number as written or a date as YYYY-MM-DD.
  public record FieldMeta(
      String name,
      String label,
      String type,
      boolean required,
      boolean unique,
      List<String> options,
      String reference,
      boolean multiple,
      String min,
      String max,
      String pattern,
      ProcessFamily.Condition requiredWhen) {}

  public record EntityMeta(
      String name,
      String label,
      List<FieldMeta> fields,
      boolean hasProcess,
      ProcessInfo process,
      String unitField,
      boolean canRead,
      boolean canWrite,
      boolean canHandle,
      boolean canAssign,
      List<String> handlerActions,
      Model.Ui ui) {}

  // held is true for a model that is being applied or needs reconciliation; it has no entities
  // then.
  public record ModelMeta(
      String name, List<Model.Unit> units, List<EntityMeta> entities, boolean held) {}

  private final ModelRegistry registry;
  private final DataService data;
  private final UserContext user;

  public MetaController(ModelRegistry registry, DataService data, UserContext user) {
    this.registry = registry;
    this.data = data;
    this.user = user;
  }

  @GetMapping
  @Operation(
      summary = "Applied models with the entities the caller can reach",
      description =
          "An entity no role of the caller reaches is left out, and so is a model with no"
              + " entity left. A model that is held is listed with held = true and no entities, only"
              + " when the caller reaches one of its entities.")
  public ApiResponse<List<ModelMeta>> models() {
    List<ModelMeta> models =
        new ArrayList<>(
            registry.all().stream()
                .map(
                    model ->
                        new ModelMeta(
                            model.name(),
                            model.units(),
                            model.entities().values().stream()
                                .filter(entity -> data.access(model, entity).read())
                                .map(entity -> describe(model, entity))
                                .toList(),
                            false))
                .filter(model -> !model.entities().isEmpty())
                .toList());
    registry.held().stream()
        .filter(
            model ->
                model.entities().values().stream()
                    .anyMatch(entity -> data.access(model, entity).read()))
        .forEach(model -> models.add(new ModelMeta(model.name(), model.units(), List.of(), true)));
    return ApiResponse.success(models);
  }

  @GetMapping("/{model}/entities/{entity}")
  @Operation(
      summary = "Fields, process and permissions of one entity for the caller",
      description =
          "handlerActions lists the process actions the roles of the caller may take,"
              + " so a client shows only the buttons that can work. process.actions[].when, when not"
              + " null, is {field, op, value}: the action is taken only on a record whose field meets"
              + " the comparison (op is one of ==, !=, <, <=, >, >=; a field without a value never"
              + " does), otherwise it is refused with 409 and data.reason CONDITION_NOT_MET.")
  public ApiResponse<EntityMeta> entity(@PathVariable String model, @PathVariable String entity) {
    Model m = registry.require(model);
    Entity e =
        m.entity(entity)
            .orElseThrow(() -> new ResourceNotFoundException("Entity " + entity + " not found"));
    return ApiResponse.success(describe(m, e));
  }

  private EntityMeta describe(Model model, Entity entity) {
    Access access = data.access(model, entity);
    List<FieldMeta> fields =
        entity.fields().values().stream()
            .map(
                f ->
                    new FieldMeta(
                        f.name(),
                        f.label(),
                        f.type().key(),
                        f.required() && f.defaultValue() == null,
                        f.unique(),
                        f.options(),
                        f.to(),
                        f.multiple(),
                        f.min(),
                        f.max(),
                        f.pattern(),
                        f.requiredWhen()))
            .toList();
    ProcessInfo process =
        entity.process() == null
            ? null
            : new ProcessInfo(
                entity.process().family(),
                entity.process().handlers(),
                entity.process().allowSelfDecision(),
                entity.process().deadline() == null
                    ? null
                    : entity.process().deadline().toSeconds(),
                ProcessInfo.EscalationInfo.of(entity.process().escalations()),
                entity.process().booking(),
                entity.process().definition().states(),
                entity.process().definition().actions());
    String unitField = entity.unitField().map(Model.Field::name).orElse(null);
    return new EntityMeta(
        entity.name(),
        entity.label(),
        fields,
        process != null,
        process,
        unitField,
        access.read(),
        access.write(),
        data.canHandle(model, entity),
        data.canAssign(model, entity),
        handlerActions(model, entity),
        entity.ui());
  }

  // The actions the roles of the caller may take on a record, as the process rules decide them.
  private List<String> handlerActions(Model model, Entity entity) {
    if (entity.process() == null) {
      return List.of();
    }
    var roles = user.clientRoles(ModelClient.of(model.name()));
    return entity.process().definition().actions().stream()
        .filter(
            action ->
                action.by() == Actor.HANDLER
                    && !Collections.disjoint(roles, entity.process().rolesFor(action)))
        .map(action -> action.name())
        .toList();
  }
}
