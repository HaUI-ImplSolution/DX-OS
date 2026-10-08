// SPDX-License-Identifier: Apache-2.0
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
@Table(name = "pesticides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pesticide {

  // Khoá chính
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Mã thuốc, dùng để tra cứu (vd: HEXACONAZOLE)
  @Column(nullable = false, unique = true)
  private String code;

  // Tên hiển thị
  @Column(nullable = false)
  private String name;

  // Đơn vị tính liều (vd: lít/ha)
  @Column(nullable = false)
  private String unit;

  // Thuốc legacy còn được giữ nếu lịch sử xử lý đang tham chiếu.
  @Builder.Default
  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean active = true;
}
