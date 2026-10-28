// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.model;

import HaUI.ImplSolution.dxos.core.api.ai.GeneratedAnswer;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "diagnoses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Diagnosis {

  // Khoá chính
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // The report keeps every diagnosis; the highest id is the current one.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_report_id", nullable = false)
  private DiseaseReport diseaseReport;

  // Bệnh được chẩn đoán
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_id", nullable = false)
  private Disease disease;

  // Độ tin cậy của chẩn đoán (0.0 - 1.0), do AI Service trả về
  @Column(nullable = false, precision = 5, scale = 4)
  private BigDecimal confidence;

  // Phiên bản model AI đã dùng để chẩn đoán
  @Column(nullable = false)
  private String modelVersion;

  // Thời điểm chẩn đoán được ghi nhận
  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant diagnosedAt;

  // Quy tắc xử lý deterministic; null khi AI chỉ có thể yêu cầu kỹ sư xem xét.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "treatment_rule_id")
  private TreatmentRule treatmentRule;

  // Khuyến nghị dạng văn bản; null với chẩn đoán do AI tạo
  @Column(columnDefinition = "TEXT")
  private String recommendationText;

  // Đoạn tài liệu AI tìm được, lưu nguyên như lúc chẩn đoán
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  @Builder.Default
  private List<KnowledgeEvidence> knowledgeEvidence = new ArrayList<>();

  // Immutable wording snapshot of this diagnosis, exposed only through its approval rules.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private GeneratedAnswer generatedExplanation;

  // Trạng thái duyệt của kỹ sư (PENDING/APPROVED/REJECTED)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

  // Người đưa ra quyết định duyệt hoặc từ chối
  private String decidedBy;

  // Claim "sub" của người ra quyết định; null với bản ghi tạo trước khi có cột này
  private String decidedByUserId;

  // Thời điểm đưa ra quyết định duyệt hoặc từ chối
  private Instant decidedAt;

  // Lý do từ chối (chỉ có giá trị khi bị REJECTED)
  private String rejectionReason;

  // Do AI hay kỹ sư tạo ra chẩn đoán này
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Builder.Default
  private DiagnosisSource source = DiagnosisSource.ENGINEER;

  // Symptoms the engineer described when recording the diagnosis; copied to the label on approval
  @Column(columnDefinition = "text")
  private String symptomText;

  // Optimistic lock: concurrent writers get a 409 instead of silently overwriting each other
  @Version
  @Column(nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
  private Long version;
}
