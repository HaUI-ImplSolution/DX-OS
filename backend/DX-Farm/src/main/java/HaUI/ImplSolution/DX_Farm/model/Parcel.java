package HaUI.ImplSolution.DX_Farm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
  @Column(nullable = false, unique = true)
  private String code;

  // Tên chủ hộ/nông dân sở hữu thửa
  @Column(nullable = false)
  private String farmerName;

  // Diện tích thửa (hecta)
  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal areaHectares;

  // Vị trí/địa chỉ thửa ruộng
  private String location;

  // Thời điểm tạo bản ghi
  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  // Thời điểm cập nhật gần nhất
  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;
}
