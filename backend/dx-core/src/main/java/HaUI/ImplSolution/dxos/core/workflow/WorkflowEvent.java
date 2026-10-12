// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// One step in the history of a case. Rows are only inserted, never updated or deleted.
@Entity
@Immutable
@Table(name = "workflow_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowEvent {

  // Primary key
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_case_id", nullable = false)
  private WorkflowCase workflowCase;

  // Step name, e.g. SUBMITTED, APPROVED, REJECTED, CANCELLED, RESUBMITTED or ASSIGNED
  @Column(nullable = false)
  private String action;

  // Null for the step that opens the case
  private String fromStatus;

  @Column(nullable = false)
  private String toStatus;

  // Token "sub" of the actor; null for history rebuilt from older data
  private String actorUserId;

  private String actorDisplayName;

  private String reason;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  // Data specific to a kind of process
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> metadata;
}
