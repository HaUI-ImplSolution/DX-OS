// SPDX-License-Identifier: Apache-2.0
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "treatment_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TreatmentApplication {

  // Khoá chính
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Vụ mùa được áp dụng xử lý
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "crop_season_id", nullable = false)
  private CropSeason cropSeason;

  // Chẩn đoán gốc dẫn tới lần phun này (có thể null nếu ghi nhận thủ công)
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "diagnosis_id", unique = true)
  private Diagnosis diagnosis;

  // Thuốc thực tế đã phun
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pesticide_id", nullable = false)
  private Pesticide pesticide;

  // Liều lượng đã phun, snapshot tại thời điểm áp dụng
  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal doseAmountSnapshot;

  // Đơn vị liều lượng, snapshot tại thời điểm áp dụng
  @Column(nullable = false)
  private String doseUnitSnapshot;

  // Số ngày cách ly, snapshot tại thời điểm áp dụng
  @Column(nullable = false)
  private Integer phiDaysSnapshot;

  // Ngày thực tế đã phun thuốc
  @Column(nullable = false)
  private LocalDate appliedAt;

  // Ngày được phép thu hoạch trở lại (appliedAt + phiDaysSnapshot)
  @Column(nullable = false)
  private LocalDate harvestLockedUntil;

  // A staff entry is CONFIRMED at once; a farmer's report waits for an engineer
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TreatmentConfirmationStatus confirmationStatus;

  // Token "sub" of the farmer who reported the spraying; null when staff recorded it
  private String reportedByUserId;

  // Token "sub" of the engineer or admin who confirmed it
  private String confirmedByUserId;

  private Instant confirmedAt;

  // Reason the engineer rejected the report, cleared when the farmer reports again
  private String rejectionReason;
}
