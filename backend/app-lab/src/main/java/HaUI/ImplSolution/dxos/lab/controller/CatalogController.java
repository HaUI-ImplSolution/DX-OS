// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.controller;

import HaUI.ImplSolution.dxos.core.api.audit.AuditEvent;
import HaUI.ImplSolution.dxos.core.api.audit.AuditRecorder;
import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.SlaPolicyRequest;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.SlaPolicyResponse;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.TicketCategoryRequest;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.TicketCategoryResponse;
import HaUI.ImplSolution.dxos.lab.model.TicketPriority;
import HaUI.ImplSolution.dxos.lab.service.SlaPolicyService;
import HaUI.ImplSolution.dxos.lab.service.TicketCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalog", description = "Ticket categories and SLA policies")
public class CatalogController {

  private final TicketCategoryService categoryService;
  private final SlaPolicyService slaPolicyService;
  private final AuditRecorder auditRecorder;

  public CatalogController(TicketCategoryService categoryService, SlaPolicyService slaPolicyService,
      AuditRecorder auditRecorder) {
    this.categoryService = categoryService;
    this.slaPolicyService = slaPolicyService;
    this.auditRecorder = auditRecorder;
  }

  @GetMapping("/ticket-categories")
  @Operation(summary = "List ticket categories by name",
      description = "Any signed-in user. Only active categories, unless includeInactive=true"
          + " (ADMIN only, otherwise ignored).")
  public ApiResponse<List<TicketCategoryResponse>> categories(
      @RequestParam(defaultValue = "false") boolean includeInactive,
      org.springframework.security.core.Authentication authentication) {
    boolean admin = authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    return ApiResponse.success(categoryService.list(includeInactive && admin));
  }

  @PostMapping("/ticket-categories")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a ticket category",
      description = "ADMIN only. 422 CATEGORY_CODE_TAKEN when the code is already used.")
  public ApiResponse<TicketCategoryResponse> createCategory(
      @Valid @RequestBody TicketCategoryRequest request) {
    TicketCategoryResponse created = categoryService.create(request);
    auditRecorder.record(AuditEvent.success("TICKET_CATEGORY_CREATED", "TICKET_CATEGORY",
        String.valueOf(created.id())));
    return ApiResponse.success("Category created", created);
  }

  @PutMapping("/ticket-categories/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Change a ticket category",
      description = "ADMIN only. The code stays as it is (400 otherwise). active=false retires the"
          + " category from the list users choose from.")
  public ApiResponse<TicketCategoryResponse> updateCategory(@PathVariable Long id,
      @Valid @RequestBody TicketCategoryRequest request) {
    TicketCategoryResponse updated = categoryService.update(id, request);
    auditRecorder.record(
        AuditEvent.success("TICKET_CATEGORY_CHANGED", "TICKET_CATEGORY", String.valueOf(id)));
    return ApiResponse.success("Category updated", updated);
  }

  @GetMapping("/sla-policies")
  @Operation(summary = "SLA policies, most urgent first",
      description = "Any signed-in user. Times are in minutes of calendar time.")
  public ApiResponse<List<SlaPolicyResponse>> slaPolicies() {
    return ApiResponse.success(slaPolicyService.list());
  }

  @PutMapping("/sla-policies/{priority}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Set the response and resolution time of a priority",
      description = "ADMIN only. Applies to tickets created afterwards. 400 when the resolution"
          + " time is shorter than the response time.")
  public ApiResponse<SlaPolicyResponse> putSlaPolicy(@PathVariable TicketPriority priority,
      @Valid @RequestBody SlaPolicyRequest request) {
    SlaPolicyResponse saved = slaPolicyService.put(priority, request);
    auditRecorder.record(AuditEvent.success("SLA_POLICY_CHANGED", "SLA_POLICY", priority.name()));
    return ApiResponse.success("SLA policy saved", saved);
  }
}
