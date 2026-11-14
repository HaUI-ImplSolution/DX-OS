// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
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
  @Operation(
      summary = "Search the audit log, newest first",
      description =
          "ADMIN only (403 otherwise). actor is a user id or username; action is an"
              + " upper-case name such as ACCESS_DENIED, AUTH_FAILED, SERVER_ERROR (a 5xx),"
              + " ADMIN_ACTION_FAILED (an administrator's change refused with 409 or 422), or one of the"
              + " changes an administrator made to accounts (USER_CREATED, USER_LOCKED, USER_UNLOCKED,"
              + " USER_PASSWORD_RESET, USER_ROLE_GRANTED, USER_ROLE_REVOKED, USER_UNIT_ADDED,"
              + " USER_UNIT_REMOVED, with the username and the role or unit in metadata);"
              + " targetType (such as USER) and targetId (the id of the account for USER) list the"
              + " records about one object, whoever made the change; outcome is SUCCESS, DENIED or FAILED (400 for another value); from is inclusive and to"
              + " exclusive, both ISO-8601 instants. size is capped at 200.")
  public ApiResponse<PageResponse<AuditLogResponse>> search(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) String targetId,
      @RequestParam(required = false) AuditOutcome outcome,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ApiResponse.success(
        queryService.search(actor, action, targetType, targetId, outcome, from, to, page, size));
  }
}
