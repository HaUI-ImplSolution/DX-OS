// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.model;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// What an engineer confirmed for a report: kept as structured data for later model training.
// Written when the diagnosis is approved; the AI service never writes it.
@Entity
@Table(name = "training_labels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingLabel {

  // Primary key
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_report_id", nullable = false)
  private DiseaseReport diseaseReport;

  // The approved diagnosis; one label per diagnosis
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "diagnosis_id", nullable = false, unique = true)
  private Diagnosis diagnosis;

  // Diagnosis of the same report that this one replaced, if any
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_diagnosis_id")
  private Diagnosis sourceDiagnosis;

  // Symptoms the engineer described; null when the diagnosis came from the AI alone
  @Column(columnDefinition = "text")
  private String symptomText;

  @Column(nullable = false)
  private String diseaseCode;

  // Token "sub" of the engineer who approved
  @Column(nullable = false)
  private String engineerUserId;

  @Column(nullable = false, updatable = false)
  private Instant labeledAt;
}
