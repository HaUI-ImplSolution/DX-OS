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
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "disease_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiseaseReport {

  // Khoá chính
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Vụ mùa được báo cáo sự cố
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "crop_season_id", nullable = false)
  private CropSeason cropSeason;

  // Tên/đường dẫn file ảnh đã upload
  @Column(nullable = false)
  private String imageRef;

  // Tên người báo cáo (nông dân)
  @Column(nullable = false)
  private String reportedBy;

  // Claim "sub" của người báo cáo; null với bản ghi tạo trước khi có cột này
  private String reportedByUserId;

  // Khoá idempotency do client sinh, dùng để gộp các lần gửi lại cùng một báo cáo
  @Column(unique = true)
  private UUID clientRequestId;

  // Thời điểm báo cáo được ghi nhận
  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant reportedAt;

  // Xóa mềm: khác null nghĩa là báo cáo đã bị admin xóa
  private Instant deletedAt;
}
