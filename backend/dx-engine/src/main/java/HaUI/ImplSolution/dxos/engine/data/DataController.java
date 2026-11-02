// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data/{model}/{entity}")
@Tag(name = "Data", description = "Records of any entity of an applied model; the model decides"
    + " the fields, the roles and the rows each role reaches")
public class DataController {

  private final ModelRegistry registry;
  private final DataService service;

  public DataController(ModelRegistry registry, DataService service) {
    this.registry = registry;
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List records",
      description = "filter.<field>=<value> (also filter.<field>.<op> with op eq, ne, lt, lte,"
          + " gt, gte, like or in), sort=<field>, order=asc|desc, page (from 0) and size (at most"
          + " 200). A role with access own sees only the records it owns; 403 when no role of the"
          + " caller reaches the entity.")
  public ApiResponse<PageResponse<Map<String, Object>>> list(@PathVariable String model,
      @PathVariable String entity, @RequestParam Map<String, String> params) {
    Model m = registry.require(model);
    return ApiResponse.success(service.list(m, entity(m, entity), params));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get one record")
  public ApiResponse<Map<String, Object>> get(@PathVariable String model,
      @PathVariable String entity, @PathVariable long id) {
    Model m = registry.require(model);
    return ApiResponse.success(service.get(m, entity(m, entity), id));
  }

  @PostMapping
  @Operation(summary = "Create a record",
      description = "400 lists the problem of each field. The status of an entity with a process"
          + " cannot be sent (403); the process sets it.")
  public ApiResponse<Map<String, Object>> create(@PathVariable String model,
      @PathVariable String entity, @RequestBody Map<String, Object> body) {
    Model m = registry.require(model);
    return ApiResponse.success(service.create(m, entity(m, entity), body));
  }

  @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
  @Operation(summary = "Change the given fields of a record")
  public ApiResponse<Map<String, Object>> update(@PathVariable String model,
      @PathVariable String entity, @PathVariable long id, @RequestBody Map<String, Object> body) {
    Model m = registry.require(model);
    return ApiResponse.success(service.update(m, entity(m, entity), id, body));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a record")
  public ApiResponse<Void> delete(@PathVariable String model, @PathVariable String entity,
      @PathVariable long id) {
    Model m = registry.require(model);
    service.delete(m, entity(m, entity), id);
    return ApiResponse.success(null);
  }

  @PostMapping("/{id}/decision")
  @Operation(summary = "Approve or reject a record",
      description = "The body is {\"outcome\": \"approve\"} or {\"outcome\": \"reject\"}. Only"
          + " the approver role of the entity may decide (403); a record already decided answers"
          + " 409.")
  public ApiResponse<Map<String, Object>> decide(@PathVariable String model,
      @PathVariable String entity, @PathVariable long id, @RequestBody Map<String, String> body) {
    Model m = registry.require(model);
    return ApiResponse.success(service.decide(m, entity(m, entity), id, body.get("outcome")));
  }

  private static Entity entity(Model model, String name) {
    return model.entity(name)
        .orElseThrow(() -> new ResourceNotFoundException("Entity " + name + " not found"));
  }
}
