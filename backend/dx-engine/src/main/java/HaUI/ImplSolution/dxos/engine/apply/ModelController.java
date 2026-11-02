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
import org.springframework.web.bind.annotation.RequestParam;
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
  @Operation(summary = "Check a model file and show what applying it would do",
      description = "The body is the model file. 400 lists every problem with its line and"
          + " column. The answer compares it with the running version: the changes by level (SAFE,"
          + " MIGRATION, LOSS), what is refused, existing rows that break a new rule, open records"
          + " of the touched processes and the statements to run. No record or table changes.")
  public ApiResponse<ApplyService.Plan> plan(@RequestBody String content) {
    return ApiResponse.success(service.plan(content));
  }

  @PostMapping(value = "/apply", consumes = {YAML, "text/plain"})
  @Operation(summary = "Apply a model file",
      description = "Brings tables, constraints, roles and units to the file and stores a new"
          + " version. To change a running model pass base, the fromVersion of the plan, and accept"
          + " with the level of the plan (SAFE by default). The same file again changes nothing or"
          + " finishes an apply that stopped midway. 409 when the plan is out of date, the level is"
          + " above accept, rows break a new rule, the change is refused, or another apply runs.")
  public ApiResponse<ApplyService.Applied> apply(@RequestBody String content,
      @RequestParam(required = false) Integer base,
      @RequestParam(required = false) ModelDiff.Level accept) {
    return ApiResponse.success(service.apply(content, user.currentUserId(), base, accept));
  }

  @PostMapping("/{model}/rollback/plan")
  @Operation(summary = "Show what returning to the version before the running one would do")
  public ApiResponse<ApplyService.Plan> planRollback(@PathVariable String model) {
    return ApiResponse.success(service.planRollback(model));
  }

  @PostMapping("/{model}/rollback")
  @Operation(summary = "Return to the version before the running one, as a new version",
      description = "Goes the same way as apply, with base and accept. A field added since then is"
          + " dropped with its values, so that needs accept=LOSS; a value removed earlier does not"
          + " come back without a backup.")
  public ApiResponse<ApplyService.Applied> rollback(@PathVariable String model,
      @RequestParam Integer base, @RequestParam(required = false) ModelDiff.Level accept) {
    return ApiResponse.success(service.rollback(model, user.currentUserId(), base, accept));
  }

  @GetMapping("/{model}/runs")
  @Operation(summary = "History of the applies of a model, newest first, with every step")
  public ApiResponse<List<Map<String, Object>>> runs(@PathVariable String model) {
    return ApiResponse.success(service.runs(model));
  }
}
