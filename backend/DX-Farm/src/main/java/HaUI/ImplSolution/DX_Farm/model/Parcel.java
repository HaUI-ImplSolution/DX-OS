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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "parcels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parcel {

  // Khoá chính
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Mã thửa ruộng, dùng làm định danh nghiệp vụ (vd: TD-01)
  // Unique among non-deleted parcels (partial index uq_parcels_code_not_deleted)
  @Column(nullable = false)
  private String code;

  // Hộ sở hữu thửa; quyền truy cập của FARMER dựa trên hộ này
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "household_id", nullable = false)
  private Household household;

  // Bản sao họ tên chủ hộ cho báo cáo/BI; không dùng cho logic phân quyền
  @Column(nullable = false)
  private String farmerName;

  // Diện tích thửa (hecta)
  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal areaHectares;

  // Vị trí/địa chỉ thửa ruộng
  private String location;

  // Toạ độ GPS (tuỳ chọn)
  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  // Thời điểm tạo bản ghi
  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  // Thời điểm cập nhật gần nhất
  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  // Xóa mềm: khác null nghĩa là thửa đã bị admin xóa
  private Instant deletedAt;
}
