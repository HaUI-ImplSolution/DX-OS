// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
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
@Tag(name = "Models", description = "Apply model files and read the apply history; ADMIN only")
public class ModelController {

  private static final String YAML = "application/yaml";

  private final ApplyService service;
  private final UserContext user;

  public ModelController(ApplyService service, UserContext user) {
    this.service = service;
    this.user = user;
  }

  @GetMapping
  @Operation(summary = "List the applied models with their current version")
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.success(service.models());
  }

  @PostMapping(value = "/plan", consumes = {YAML, "text/plain"})
  @Operation(summary = "Check a model file and list the steps an apply would run",
      description = "The body is the model file. 400 lists every problem with its line and"
          + " column. Nothing is changed.")
  public ApiResponse<ApplyService.Plan> plan(@RequestBody String content) {
    return ApiResponse.success(service.plan(content));
  }

  @PostMapping(value = "/apply", consumes = {YAML, "text/plain"})
  @Operation(summary = "Apply a model file",
      description = "Creates the schema, tables, constraints and roles of the model and stores"
          + " its version. Applying the same file again changes nothing or finishes an apply that"
          + " stopped midway. 409 for a different file while the model is applied or unfinished,"
          + " and while another apply of the model runs.")
  public ApiResponse<ApplyService.Applied> apply(@RequestBody String content) {
    return ApiResponse.success(service.apply(content, user.currentUserId()));
  }

  @GetMapping("/{model}/runs")
  @Operation(summary = "History of the applies of a model, newest first, with every step")
  public ApiResponse<List<Map<String, Object>>> runs(@PathVariable String model) {
    return ApiResponse.success(service.runs(model));
  }
}
