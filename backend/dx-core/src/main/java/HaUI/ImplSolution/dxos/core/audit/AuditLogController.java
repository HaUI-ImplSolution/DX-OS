// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.audit;

import HaUI.ImplSolution.dxos.core.api.audit.AuditOutcome;
import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit", description = "Refused requests and administrative actions")
public class AuditLogController {

  private final AuditLogQueryService queryService;

  public AuditLogController(AuditLogQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Search the audit log, newest first",
      description = "ADMIN only (403 otherwise). actor is a user id or username; action is an"
          + " upper-case name such as ACCESS_DENIED, AUTH_FAILED, SERVER_ERROR (a 5xx),"
          + " ADMIN_ACTION_FAILED (an administrator's change refused with 409 or 422) or"
          + " WORKSPACE_CREATED; outcome is"
          + " SUCCESS, DENIED or FAILED (400 for another value); from is inclusive and to"
          + " exclusive, both ISO-8601 instants. size is capped at 200.")
  public ApiResponse<PageResponse<AuditLogResponse>> search(
      @RequestParam(required = false) String actor, @RequestParam(required = false) String action,
      @RequestParam(required = false) AuditOutcome outcome,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return ApiResponse.success(queryService.search(actor, action, outcome, from, to, page, size));
  }
}
