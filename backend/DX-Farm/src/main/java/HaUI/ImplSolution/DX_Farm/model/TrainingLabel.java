package HaUI.ImplSolution.DX_Farm.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "training_labels")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingLabel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_report_id", nullable = false)
  private DiseaseReport diseaseReport;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "diagnosis_id", nullable = false, unique = true)
  private Diagnosis diagnosis;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_diagnosis_id")
  private Diagnosis sourceDiagnosis;
  @Column(columnDefinition = "text")
  private String symptomText;
  @Column(nullable = false)
  private String diseaseCode;
  @Column(nullable = false)
  private String engineerUserId;
  @Column(nullable = false, updatable = false)
  private Instant labeledAt;
}
