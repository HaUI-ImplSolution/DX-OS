// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.meta;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.engine.data.DataService;
import HaUI.ImplSolution.dxos.engine.data.DataService.Access;
import HaUI.ImplSolution.dxos.engine.process.ProcessInfo;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta/models")
@Tag(name = "Metadata", description = "What the applied models contain, as seen by the caller")
public class MetaController {

  public record FieldMeta(String name, String label, String type, boolean required, boolean unique,
      List<String> options, String reference) {}

  public record EntityMeta(String name, String label, List<FieldMeta> fields, boolean hasProcess,
      ProcessInfo process, boolean canRead, boolean canWrite, boolean canDecide) {}

  public record ModelMeta(String name, List<EntityMeta> entities) {}

  private final ModelRegistry registry;
  private final DataService data;

  public MetaController(ModelRegistry registry, DataService data) {
    this.registry = registry;
    this.data = data;
  }

  @GetMapping
  @Operation(summary = "Applied models with the entities the caller can reach",
      description = "An entity no role of the caller reaches is left out, and so is a model with no"
          + " entity left.")
  public ApiResponse<List<ModelMeta>> models() {
    return ApiResponse.success(registry.all().stream()
        .map(model -> new ModelMeta(model.name(),
            model.entities().values().stream().filter(entity -> data.access(entity).read())
                .map(entity -> describe(entity)).toList()))
        .filter(model -> !model.entities().isEmpty()).toList());
  }

  @GetMapping("/{model}/entities/{entity}")
  @Operation(summary = "Fields, process and permissions of one entity for the caller")
  public ApiResponse<EntityMeta> entity(@PathVariable String model, @PathVariable String entity) {
    Model m = registry.require(model);
    Entity e = m.entity(entity)
        .orElseThrow(() -> new ResourceNotFoundException("Entity " + entity + " not found"));
    return ApiResponse.success(describe(e));
  }

  private EntityMeta describe(Entity entity) {
    Access access = data.access(entity);
    List<FieldMeta> fields = entity.fields().values().stream().map(f -> new FieldMeta(f.name(),
        f.label(), f.type().key(), f.required(), f.unique(), f.options(), f.to())).toList();
    ProcessInfo process = entity.process() == null ? null
        : new ProcessInfo(entity.process().template(), entity.process().approver(),
            entity.process().allowSelfDecision());
    return new EntityMeta(entity.name(), entity.label(), fields, process != null, process,
        access.read(), access.write(), data.canDecide(entity));
  }
}
