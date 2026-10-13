// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.dto;

import HaUI.ImplSolution.dxos.lab.model.DeviceLoan;
import HaUI.ImplSolution.dxos.lab.model.DeviceMaintenance;
import HaUI.ImplSolution.dxos.lab.model.MaintenanceKind;
import HaUI.ImplSolution.dxos.lab.model.ReturnCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

// Requests and responses of device maintenance and loans.
public final class DeviceDtos {

  public record MaintenanceStartRequest(@NotNull MaintenanceKind kind,
      @NotBlank @Size(max = 2000) String note, Long ticketId) {}

  public record MaintenanceCompleteRequest(@Size(max = 2000) String note) {}

  public record MaintenanceResponse(Long id, Long deviceId, MaintenanceKind kind, String note,
      Long ticketId, String performedById, String performedByName, Instant startedAt,
      Instant completedAt, String completionNote) {

    public static MaintenanceResponse of(DeviceMaintenance entry) {
      return new MaintenanceResponse(entry.getId(), entry.getDevice().getId(), entry.getKind(),
          entry.getNote(), entry.getTicket() == null ? null : entry.getTicket().getId(),
          entry.getPerformedById(), entry.getPerformedByName(), entry.getStartedAt(),
          entry.getCompletedAt(), entry.getCompletionNote());
    }
  }

  public record LoanCreateRequest(@NotNull Long deviceId, @NotBlank @Size(max = 500) String purpose,
      @NotNull LocalDate dueDate) {}

  public record LoanReturnRequest(@NotNull ReturnCondition condition,
      @Size(max = 1000) String note) {}

  public record LoanRejectRequest(@Size(max = 1000) String reason) {}

  public record LoanResponse(Long id, Long deviceId, String deviceAssetCode, String deviceType,
      String borrowerId, String borrowerName, String borrowerRole, String purpose,
      LocalDate dueDate, String status, String approvedByName, Instant approvedAt,
      String rejectionReason, Instant returnedAt, ReturnCondition returnCondition,
      String returnNote, boolean overdue, Instant createdAt) {

    public static LoanResponse of(DeviceLoan loan, LocalDate today) {
      boolean overdue = "APPROVED".equals(loan.getStatus()) && loan.getDueDate().isBefore(today);
      return new LoanResponse(loan.getId(), loan.getDevice().getId(),
          loan.getDevice().getAssetCode(), loan.getDevice().getDeviceType(), loan.getBorrowerId(),
          loan.getBorrowerName(), loan.getBorrowerRole(), loan.getPurpose(), loan.getDueDate(),
          loan.getStatus(), loan.getApprovedByName(), loan.getApprovedAt(),
          loan.getRejectionReason(), loan.getReturnedAt(), loan.getReturnCondition(),
          loan.getReturnNote(), overdue, loan.getCreatedAt());
    }
  }

  private DeviceDtos() {}
}
