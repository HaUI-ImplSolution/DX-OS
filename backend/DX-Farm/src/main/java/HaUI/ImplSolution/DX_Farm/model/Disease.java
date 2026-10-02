package HaUI.ImplSolution.DX_Farm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "diseases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Disease {

  // Khoá chính
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Mã bệnh, dùng để tra cứu (vd: BROWN_SPOT)
  @Column(nullable = false, unique = true)
  private String code;

  // Tên hiển thị tiếng Việt
  @Column(nullable = false)
  private String name;

  // Mô tả chi tiết về bệnh
  private String description;

  // Bản ghi legacy vẫn được giữ khi lịch sử chẩn đoán còn tham chiếu.
  @Builder.Default
  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean active = true;
}
