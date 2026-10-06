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
import jakarta.persistence.Version;
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

  // Khoá chính
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Báo cáo sự cố mà chẩn đoán này thuộc về (1-1)
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_report_id", nullable = false, unique = true)
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

  // Khuyến nghị dạng văn bản sinh ra từ treatmentRule
  @Column(nullable = false, columnDefinition = "TEXT")
  private String recommendationText;

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

  // Optimistic lock: concurrent writers get a 409 instead of silently overwriting each other
  @Version
  @Column(nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
  private Long version;
}
