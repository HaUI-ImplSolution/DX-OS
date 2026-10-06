package HaUI.ImplSolution.DX_Farm.core.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "workflow_case")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowCase {

  // Primary key
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Kind of process, e.g. DIAGNOSIS_APPROVAL
  @Column(nullable = false)
  private String caseType;

  // Entity being processed: its type and id in the application (polymorphic, no foreign key)
  @Column(nullable = false)
  private String entityType;

  @Column(nullable = false)
  private Long entityId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WorkflowStatus status;

  // Token "sub" of whoever opened the case; null for cases rebuilt from older data
  private String createdBy;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  // Token "sub" of the assignee; null while nobody has taken the case
  private String assignedTo;

  private String assignedToName;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  // Optimistic lock: two people deciding the same case get a 409 instead of overwriting.
  @Version
  @Column(nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
  private Long version;
}
