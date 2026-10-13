// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

// A request to borrow a device. Its status mirrors the DEVICE_LOAN workflow case.
@Entity
@Table(name = "device_loans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceLoan {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "device_id", nullable = false)
  private Device device;

  @Column(nullable = false)
  private String borrowerId;

  @Column(nullable = false)
  private String borrowerName;

  // Highest Lab role of the borrower when asking; decides who may approve
  @Column(nullable = false)
  private String borrowerRole;

  private Long workspaceId;

  @Column(nullable = false, length = 500)
  private String purpose;

  // Last day the device may be kept
  @Column(nullable = false)
  private LocalDate dueDate;

  // Status of the DEVICE_LOAN case, see LoanStatus
  @Column(nullable = false)
  private String status;

  private String approvedById;

  private String approvedByName;

  private Instant approvedAt;

  @Column(length = 1000)
  private String rejectionReason;

  private Instant returnedAt;

  @Enumerated(EnumType.STRING)
  private ReturnCondition returnCondition;

  @Column(length = 1000)
  private String returnNote;

  private Long workflowCaseId;

  @Column(unique = true)
  private UUID clientRequestId;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  // Optimistic lock: concurrent writers get a 409 instead of silently overwriting each other
  @Version
  @Column(nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
  private Long version;
}
