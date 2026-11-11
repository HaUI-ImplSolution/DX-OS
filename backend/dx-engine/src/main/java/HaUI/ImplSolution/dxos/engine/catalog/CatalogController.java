// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.catalog;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "Model catalog",
    description = "What the control panel reads to help write a model; ADMIN only")
public class CatalogController {

  private final CatalogService catalog;
  private final ProcessDiagramService diagrams;
  private final DataCatalogService dataCatalog;
  private final ModelRegistry registry;

  public CatalogController(
      CatalogService catalog,
      ProcessDiagramService diagrams,
      DataCatalogService dataCatalog,
      ModelRegistry registry) {
    this.catalog = catalog;
    this.diagrams = diagrams;
    this.dataCatalog = dataCatalog;
    this.registry = registry;
  }

  @GetMapping("/schema")
  @Operation(summary = "The JSON Schema of the model file")
  public ApiResponse<Map<String, Object>> schema() {
    return ApiResponse.success(catalog.schema());
  }

  @GetMapping("/families")
  @Operation(summary = "The process families with their states, actions and parameters")
  public ApiResponse<List<Map<String, Object>>> families() {
    return ApiResponse.success(catalog.families());
  }

  @PostMapping(
      value = "/diagram",
      consumes = {"application/yaml", "text/plain"})
  @Operation(
      summary = "The process diagrams of a model file",
      description =
          "The body is the model file; it is only read, never compared with the running"
              + " model. Answers 200 with valid false and every problem with its line and column (line"
              + " 0 for a problem without one) when the file is not a model. Otherwise one entry per"
              + " entity that has a process: the states and actions of its family, the completions of"
              + " each final state, the handler, the deadline and auto_close in seconds (null when"
              + " not set), the booking, and the states where the deadline runs.")
  public ApiResponse<ProcessDiagramService.Diagram> diagram(@RequestBody String content) {
    return ApiResponse.success(diagrams.diagram(content));
  }

  @GetMapping("/{model}/data-catalog")
  @Operation(
      summary = "The data of an applied model, read from the model",
      description =
          "For each entity: its fields, the roles that reach it and the rows it holds"
              + " (-1 when the table cannot be read). links are the references between entities."
              + " origins are the roles that reach the model and the outside applications registered"
              + " for it; destinations are the webhook target, the completion actions of the"
              + " processes, the event feed and the archive. 404 when the model is not applied.")
  public ApiResponse<DataCatalogService.Catalog> dataCatalog(@PathVariable String model) {
    return ApiResponse.success(dataCatalog.of(registry.require(model)));
  }

  @GetMapping("/templates")
  @Operation(summary = "The model templates, each with its file content")
  public ApiResponse<List<Map<String, Object>>> templates() {
    return ApiResponse.success(catalog.templates());
  }

  @GetMapping("/{model}/versions")
  @Operation(
      summary = "The applied versions of a model, newest first",
      description =
          "applied_by is the user id of who applied the version, applied_by_name the name"
              + " the token gave then (empty for versions saved without one). 404 when no version of the"
              + " model was applied.")
  public ApiResponse<List<Map<String, Object>>> versions(@PathVariable String model) {
    return ApiResponse.success(catalog.versions(model));
  }

  @GetMapping("/{model}/versions/{version}")
  @Operation(summary = "One applied version of a model with its file content")
  public ApiResponse<Map<String, Object>> version(
      @PathVariable String model, @PathVariable int version) {
    return ApiResponse.success(catalog.version(model, version));
  }
}
