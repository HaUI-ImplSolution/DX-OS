// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.assistant;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistant", description = "Read-only assistance over the caller's permitted records")
public class AssistantController {
  private final AssistantService service;

  public AssistantController(AssistantService service) {
    this.service = service;
  }

  @GetMapping("/capabilities")
  @Operation(summary = "Read assistant capabilities without calling AI",
      description = "Available when AI is disabled; contains no business data.")
  public ApiResponse<AssistantContract.Capabilities> capabilities() {
    return ApiResponse.success(service.capabilities());
  }

  @PostMapping("/query")
  @Operation(summary = "Find, summarize or compare permitted records",
      description = "Reads one selected entity under the caller's normal row permissions. "
          + "Filters use the list API's field/operator syntax, without the filter. prefix. "
          + "interpretQuery opts into model-proposed filters, intersected with user filters. "
          + "Filter-only results use Backend totals and generationStatus NOT_NEEDED. "
          + "Oversized context requires narrowing; changed sources return STALE_CONTEXT. "
          + "Generated text is optional and never performs business actions.")
  public ApiResponse<AssistantContract.Result> query(
      @Valid @RequestBody AssistantContract.Query query) {
    return ApiResponse.success(service.query(query));
  }
}
