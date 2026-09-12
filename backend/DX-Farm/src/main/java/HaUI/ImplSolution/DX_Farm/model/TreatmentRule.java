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
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "treatment_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TreatmentRule {

  // Khoá chính
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Bệnh áp dụng quy tắc này
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_id", nullable = false)
  private Disease disease;

  // Thuốc khuyến cáo dùng để xử lý
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pesticide_id", nullable = false)
  private Pesticide pesticide;

  // Liều lượng khuyến cáo (cố định)
  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal doseAmount;

  // Khoảng liều lượng min-max
  @Column(name = "dose_min", precision = 10, scale = 2)
  private BigDecimal doseMin;

  @Column(name = "dose_max", precision = 10, scale = 2)
  private BigDecimal doseMax;

  // Đơn vị của liều lượng (vd: lít/ha)
  @Column(nullable = false)
  private String doseUnit;

  // Số ngày cách ly trước khi được thu hoạch (PHI)
  @Column(nullable = false)
  private Integer phiDays;

  // Ghi chú thêm về cách dùng
  private String notes;

  // Nguồn tham chiếu quy tắc (vd: VietGAP)
  private String source;

  // Phiên bản quy tắc
  private String version;
}
