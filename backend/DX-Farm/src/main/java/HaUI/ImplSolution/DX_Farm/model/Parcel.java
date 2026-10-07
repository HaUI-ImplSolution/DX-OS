// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.model;

import HaUI.ImplSolution.DX_Farm.core.identity.Household;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
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

  // Primary key
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Generated parcel code (e.g. HTX01-H0042-T03); older parcels keep their hand-typed code (e.g.
  // TD-01)
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

  // Parcel area in hectares
  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal areaHectares;

  // Parcel location or address
  private String location;

  // GPS position; required for a new parcel, may be missing on older ones (locationMissing)
  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  // Error radius of the GPS fix in meters; null when an admin entered the position
  @Column(precision = 7, scale = 2)
  private BigDecimal gpsAccuracyMeters;

  // Parcel without a position, so it is not shown on the map
  @Column(nullable = false)
  @Builder.Default
  private boolean locationMissing = false;

  // Approval state, mirrors the PARCEL_APPROVAL workflow case
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ApprovalStatus approvalStatus;

  // Reason the admin rejected it, cleared on resubmit
  private String rejectionReason;

  // Idempotency-Key of the request that created the parcel
  @Column(unique = true)
  private UUID clientRequestId;

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
