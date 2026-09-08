package HaUI.ImplSolution.DX_Farm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "diagnoses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Diagnosis {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_report_id", nullable = false, unique = true)
  private DiseaseReport diseaseReport;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_id", nullable = false)
  private Disease disease;

  @Column(nullable = false, precision = 5, scale = 4)
  private BigDecimal confidence;

  @Column(nullable = false)
  private String modelVersion;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant diagnosedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "treatment_rule_id", nullable = false)
  private TreatmentRule treatmentRule;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String recommendationText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

  private String approvedBy;

  private Instant approvedAt;

  private String rejectionReason;
}
