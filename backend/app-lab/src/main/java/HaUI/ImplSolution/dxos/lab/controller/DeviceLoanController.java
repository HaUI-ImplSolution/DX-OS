// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.controller;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanCreateRequest;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanRejectRequest;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanResponse;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanReturnRequest;
import HaUI.ImplSolution.dxos.lab.service.DeviceLoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device-loans")
@Tag(name = "DeviceLoan", description = "Borrowing and returning devices - Process")
public class DeviceLoanController {

  private final DeviceLoanService loanService;

  public DeviceLoanController(DeviceLoanService loanService) {
    this.loanService = loanService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Ask to borrow a device",
      description = "Any signed-in user. dueDate is today to 7 days ahead (422"
          + " LOAN_PERIOD_INVALID). 422 DEVICE_NOT_AVAILABLE unless the device is AVAILABLE;"
          + " 422 TOO_MANY_OPEN_LOANS with 2 loans waiting or not returned. A student's loan"
          + " waits for a lecturer, a lecturer's for a technician. With an Idempotency-Key, a"
          + " repeated request returns the existing loan.")
  public ApiResponse<LoanResponse> create(@Valid @RequestBody LoanCreateRequest request,
      @Parameter(description = "Client-generated UUID that makes retries of this request safe")
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    UUID clientRequestId = idempotencyKey == null ? null : UUID.fromString(idempotencyKey);
    return ApiResponse.success("Loan requested", loanService.create(request, clientRequestId));
  }

  @GetMapping
  @Operation(summary = "List loans, newest first",
      description = "Own loans for STUDENT; LECTURER, TECHNICIAN and ADMIN see all of them (mine=true"
          + " limits the list to their own). Filters: status, deviceId. size is capped at 100.")
  public ApiResponse<PageResponse<LoanResponse>> list(@RequestParam(required = false) String status,
      @RequestParam(required = false) Long deviceId,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(loanService.list(status, deviceId, mine, page, size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a loan",
      description = "404 for the loan of someone else unless" + " LECTURER, TECHNICIAN or ADMIN.")
  public ApiResponse<LoanResponse> get(@PathVariable Long id) {
    return ApiResponse.success(loanService.get(id));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('LECTURER','TECHNICIAN','ADMIN')")
  @Operation(summary = "Approve a loan; the device becomes ON_LOAN",
      description = "A lecturer approves a student's loan, a technician a lecturer's, an ADMIN any"
          + " (403 otherwise, and never your own). 409 unless PENDING. 422 DEVICE_NOT_AVAILABLE"
          + " when the device is no longer free.")
  public ApiResponse<LoanResponse> approve(@PathVariable Long id) {
    return ApiResponse.success("Loan approved", loanService.approve(id));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('LECTURER','TECHNICIAN','ADMIN')")
  @Operation(summary = "Reject a loan",
      description = "Same approvers as approve. reason is required (422"
          + " REJECTION_REASON_REQUIRED). 409 unless PENDING.")
  public ApiResponse<LoanResponse> reject(@PathVariable Long id,
      @Valid @RequestBody(required = false) LoanRejectRequest request) {
    return ApiResponse.success("Loan rejected", loanService.reject(id, request));
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Withdraw a loan nobody decided yet",
      description = "The borrower or an ADMIN. 409 unless PENDING.")
  public ApiResponse<LoanResponse> cancel(@PathVariable Long id) {
    return ApiResponse.success("Loan cancelled", loanService.cancel(id));
  }

  @PostMapping("/{id}/return")
  @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
  @Operation(summary = "Take a device back and record its condition",
      description = "TECHNICIAN or ADMIN only. GOOD makes the device AVAILABLE. DAMAGED needs a"
          + " note (422 RETURN_NOTE_REQUIRED), sets the device to UNDER_REPAIR and opens a"
          + " maintenance entry. 409 unless APPROVED.")
  public ApiResponse<LoanResponse> returnDevice(@PathVariable Long id,
      @Valid @RequestBody LoanReturnRequest request) {
    return ApiResponse.success("Device returned", loanService.returnDevice(id, request));
  }
}
