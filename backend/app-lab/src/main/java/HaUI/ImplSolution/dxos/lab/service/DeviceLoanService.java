// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ActionNotAllowedException;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations;
import HaUI.ImplSolution.dxos.lab.LabSecurityPolicies;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanCreateRequest;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanRejectRequest;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanResponse;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.LoanReturnRequest;
import HaUI.ImplSolution.dxos.lab.model.Device;
import HaUI.ImplSolution.dxos.lab.model.DeviceLoan;
import HaUI.ImplSolution.dxos.lab.model.DeviceStatus;
import HaUI.ImplSolution.dxos.lab.model.LoanStatus;
import HaUI.ImplSolution.dxos.lab.model.ReturnCondition;
import HaUI.ImplSolution.dxos.lab.repository.DeviceLoanRepository;
import HaUI.ImplSolution.dxos.lab.repository.DeviceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Borrowing of devices: students ask a lecturer, lecturers ask a technician, and a technician or
// administrator takes the device back.
@Service
@Transactional(readOnly = true)
public class DeviceLoanService {

  public static final int MAX_LOAN_DAYS = 7;
  public static final int MAX_OPEN_LOANS = 2;

  private static final int MAX_PAGE_SIZE = 100;

  private final DeviceLoanRepository loanRepository;
  private final DeviceRepository deviceRepository;
  private final DeviceMaintenanceService maintenanceService;
  private final WorkflowOperations workflow;
  private final LabUser user;
  private final Clock clock;

  public DeviceLoanService(DeviceLoanRepository loanRepository, DeviceRepository deviceRepository,
      DeviceMaintenanceService maintenanceService, WorkflowOperations workflow, LabUser user,
      Clock clock) {
    this.loanRepository = loanRepository;
    this.deviceRepository = deviceRepository;
    this.maintenanceService = maintenanceService;
    this.workflow = workflow;
    this.user = user;
    this.clock = clock;
  }

  @Transactional
  public LoanResponse create(LoanCreateRequest request, UUID clientRequestId) {
    if (clientRequestId != null) {
      var existing = loanRepository.findByClientRequestId(clientRequestId);
      if (existing.isPresent()) {
        return toResponse(existing.get());
      }
    }
    Device device = deviceRepository.findById(request.deviceId())
        .orElseThrow(() -> new IllegalArgumentException("Unknown device: " + request.deviceId()));
    LocalDate today = today();
    if (request.dueDate().isBefore(today)
        || request.dueDate().isAfter(today.plusDays(MAX_LOAN_DAYS))) {
      throw new PokaYokeViolationException("LOAN_PERIOD_INVALID",
          "Thời hạn mượn tối đa %d ngày kể từ hôm nay".formatted(MAX_LOAN_DAYS));
    }
    requireAvailable(device);
    long open = loanRepository.countByBorrowerIdAndStatusIn(user.id(),
        List.of(LoanStatus.PENDING, LoanStatus.APPROVED));
    if (open >= MAX_OPEN_LOANS) {
      throw new PokaYokeViolationException("TOO_MANY_OPEN_LOANS",
          "Bạn đang có %d phiếu mượn chưa trả, tối đa là %d".formatted(open, MAX_OPEN_LOANS));
    }
    DeviceLoan loan = loanRepository.save(DeviceLoan.builder().device(device).borrowerId(user.id())
        .borrowerName(user.name()).borrowerRole(user.highestRole()).workspaceId(user.workspaceId())
        .purpose(request.purpose().strip()).dueDate(request.dueDate()).status(LoanStatus.PENDING)
        .clientRequestId(clientRequestId).build());
    CaseView workflowCase = workflow.createCase(LabCaseTypes.DEVICE_LOAN,
        LabCaseTypes.DEVICE_LOAN_ENTITY, loan.getId(), user.actor());
    loan.setWorkflowCaseId(workflowCase.id());
    return toResponse(loanRepository.save(loan));
  }

  // Own loans for most users; lecturers, technicians and administrators see all of them.
  public PageResponse<LoanResponse> list(String status, Long deviceId, boolean mine, int page,
      int size) {
    Specification<DeviceLoan> spec = Specification.unrestricted();
    if (mine || !user.seesAllLoans()) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("borrowerId"), user.id()));
    }
    if (status != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
    }
    if (deviceId != null) {
      spec = spec.and((root, q, cb) -> cb.equal(root.get("device").get("id"), deviceId));
    }
    PageRequest pageRequest =
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    LocalDate today = today();
    return PageResponse.of(loanRepository.findAll(spec, pageRequest),
        loan -> LoanResponse.of(loan, today));
  }

  public LoanResponse get(Long id) {
    return toResponse(findVisible(id));
  }

  @Transactional
  public LoanResponse approve(Long id) {
    DeviceLoan loan = findVisible(id);
    requireStatus(loan, LoanStatus.PENDING);
    requireApprover(loan);
    if (loan.getDueDate().isBefore(today())) {
      throw new PokaYokeViolationException("LOAN_PERIOD_EXPIRED",
          "Hạn trả đã qua, hãy tạo phiếu mượn mới");
    }
    loan.setApprovedById(user.id());
    loan.setApprovedByName(user.name());
    loan.setApprovedAt(clock.instant());
    workflow.decide(loan.getWorkflowCaseId(), LoanStatus.APPROVED, user.actor(), null);
    return toResponse(loan);
  }

  @Transactional
  public LoanResponse reject(Long id, LoanRejectRequest request) {
    DeviceLoan loan = findVisible(id);
    requireStatus(loan, LoanStatus.PENDING);
    requireApprover(loan);
    String reason = request == null ? null : request.reason();
    if (reason == null || reason.isBlank()) {
      throw new PokaYokeViolationException("REJECTION_REASON_REQUIRED",
          "Cần ghi lý do từ chối phiếu mượn");
    }
    workflow.decide(loan.getWorkflowCaseId(), LoanStatus.REJECTED, user.actor(), reason.strip());
    return toResponse(loan);
  }

  @Transactional
  public LoanResponse cancel(Long id) {
    DeviceLoan loan = findVisible(id);
    requireStatus(loan, LoanStatus.PENDING);
    if (!user.isAdmin() && !loan.getBorrowerId().equals(user.id())) {
      throw new ActionNotAllowedException("Only the borrower can cancel the loan");
    }
    workflow.decide(loan.getWorkflowCaseId(), LoanStatus.CANCELLED, user.actor(), null);
    return toResponse(loan);
  }

  // A damaged device goes straight to repair with a log entry.
  @Transactional
  public LoanResponse returnDevice(Long id, LoanReturnRequest request) {
    DeviceLoan loan = findVisible(id);
    requireStatus(loan, LoanStatus.APPROVED);
    String note =
        request.note() == null || request.note().isBlank() ? null : request.note().strip();
    if (request.condition() == ReturnCondition.DAMAGED && note == null) {
      throw new PokaYokeViolationException("RETURN_NOTE_REQUIRED",
          "Hãy mô tả hư hỏng khi nhận lại thiết bị");
    }
    loan.setReturnCondition(request.condition());
    loan.setReturnNote(note);
    loan.setReturnedAt(clock.instant());
    if (request.condition() == ReturnCondition.DAMAGED) {
      Device device = deviceRepository.findByIdForUpdate(loan.getDevice().getId())
          .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
      maintenanceService.openRepairForDamagedReturn(device,
          "Hỏng khi trả phiếu mượn #%d: %s".formatted(loan.getId(), note));
    }
    workflow.decide(loan.getWorkflowCaseId(), LoanStatus.RETURNED, user.actor(), null);
    return toResponse(loan);
  }

  private DeviceLoan findVisible(Long id) {
    DeviceLoan loan = loanRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Device loan not found: " + id));
    if (!user.seesAllLoans() && !loan.getBorrowerId().equals(user.id())) {
      throw new ResourceNotFoundException("Device loan not found: " + id);
    }
    return loan;
  }

  private static void requireStatus(DeviceLoan loan, String expected) {
    if (!expected.equals(loan.getStatus())) {
      throw new InvalidStateException(
          "Loan %d is %s, expected %s".formatted(loan.getId(), loan.getStatus(), expected));
    }
  }

  private static void requireAvailable(Device device) {
    if (device.getStatus() != DeviceStatus.AVAILABLE) {
      throw new PokaYokeViolationException("DEVICE_NOT_AVAILABLE",
          "Thiết bị %s hiện không sẵn sàng (%s)".formatted(device.getAssetCode(),
              device.getStatus()));
    }
  }

  // A student's loan is approved by a lecturer and a lecturer's by a technician; an administrator
  // approves any. Nobody approves their own.
  private void requireApprover(
      DeviceLoan loan) {if(loan.getBorrowerId().equals(user.id())){throw new ActionNotAllowedException("You cannot decide your own loan");}if(user.isAdmin()){return;}boolean allowed=switch(loan.getBorrowerRole()){case LabSecurityPolicies.STUDENT->user.isLecturer();case LabSecurityPolicies.LECTURER->user.isTechnician();default->false;};if(!allowed){throw new ActionNotAllowedException("A %s loan needs approval from a higher role".formatted(loan.getBorrowerRole()));}}

  private LoanResponse toResponse(DeviceLoan loan) {
    return LoanResponse.of(loan, today());
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
