package HaUI.ImplSolution.DX_Farm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "diagnosis_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiagnosisJob {

  @Id
  @Column(length = 36)
  private String id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_report_id", nullable = false, unique = true)
  private DiseaseReport diseaseReport;

  @Column(nullable = false, unique = true, length = 80)
  private String requestId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DiagnosisJobStatus status;

  @Column(nullable = false, length = 64)
  private String stage;

  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  private Instant completedAt;
}
