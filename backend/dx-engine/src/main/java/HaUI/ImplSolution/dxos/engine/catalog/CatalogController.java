// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.catalog;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Model catalog",
    description = "What the control panel reads to help write a model; ADMIN only")
public class CatalogController {

  private final CatalogService catalog;

  public CatalogController(CatalogService catalog) {
    this.catalog = catalog;
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

  @GetMapping("/templates")
  @Operation(summary = "The model templates, each with its file content")
  public ApiResponse<List<Map<String, Object>>> templates() {
    return ApiResponse.success(catalog.templates());
  }

  @GetMapping("/{model}/versions")
  @Operation(summary = "The applied versions of a model, newest first",
      description = "404 when no version of the model was applied.")
  public ApiResponse<List<Map<String, Object>>> versions(@PathVariable String model) {
    return ApiResponse.success(catalog.versions(model));
  }

  @GetMapping("/{model}/versions/{version}")
  @Operation(summary = "One applied version of a model with its file content")
  public ApiResponse<Map<String, Object>> version(@PathVariable String model,
      @PathVariable int version) {
    return ApiResponse.success(catalog.version(model, version));
  }
}
