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

// An IT support request. Its status mirrors the TICKET workflow case.
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Generated code TK-<year>-<number>
  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, length = 4000)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private TicketCategory category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TicketPriority priority;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id")
  private Room room;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "device_id")
  private Device device;

  // Workspace (class or department) of the requester when the account is linked to one
  private Long workspaceId;

  // Token "sub" and display name of whoever reported it
  @Column(nullable = false)
  private String requesterId;

  @Column(nullable = false)
  private String requesterName;

  // Status of the TICKET case, see TicketStatus
  @Column(nullable = false)
  private String status;

  // Token "sub" and display name of the technician working on it
  private String assigneeId;

  private String assigneeName;

  private Long workflowCaseId;

  // SLA deadlines, fixed when the ticket is created
  @Column(nullable = false)
  private Instant responseDueAt;

  @Column(nullable = false)
  private Instant resolutionDueAt;

  // First time staff started work or answered in public
  private Instant firstRespondedAt;

  private Instant resolvedAt;

  private Instant closedAt;

  @Column(length = 2000)
  private String resolutionNote;

  @Column(length = 1000)
  private String rootCause;

  @Column(nullable = false)
  @Builder.Default
  private Integer reopenCount = 0;

  // 1-5 stars given by the requester when closing
  private Integer rating;

  @Column(length = 1000)
  private String ratingComment;

  // Set once the 80 percent warning has been sent
  private Instant slaWarnedAt;

  @Column(nullable = false)
  @Builder.Default
  private boolean overdue = false;

  private Instant overdueAt;

  // Idempotency-Key of the request that created the ticket
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
