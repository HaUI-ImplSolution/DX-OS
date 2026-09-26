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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crop_seasons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropSeason {

  // Primary key
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Thửa ruộng canh tác vụ mùa này
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parcel_id", nullable = false)
  private Parcel parcel;

  // Generated season code (e.g. HTX01-H0042-T03-2026DX); absent on older seasons
  private String code;

  // Season type; absent on older seasons
  @Enumerated(EnumType.STRING)
  private SeasonType seasonType;

  // Year of the start date, used for the one-season-per-type-per-year rule
  private Integer seasonYear;

  // Variety name; for a new season the name of the catalogue variety
  @Column(nullable = false)
  private String riceVariety;

  // Catalogue variety code; absent on older seasons
  private String riceVarietyCode;

  // Reason given when the expected harvest date is outside the growth range of the variety
  private String reasonForOverride;

  // Approval state, mirrors the CROP_SEASON_APPROVAL workflow case
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ApprovalStatus approvalStatus;

  // Reason the admin rejected it, cleared on resubmit
  private String rejectionReason;

  // Idempotency-Key of the request that created the season
  @Column(unique = true)
  private UUID clientRequestId;

  // Ngày bắt đầu vụ
  @Column(nullable = false)
  private LocalDate startDate;

  // Ngày dự kiến thu hoạch
  private LocalDate expectedHarvestDate;

  // Trạng thái vụ mùa (ACTIVE/HARVESTED)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private CropSeasonStatus status = CropSeasonStatus.ACTIVE;

  // Thời điểm thực tế đã thu hoạch
  private Instant harvestedAt;

  // Xóa mềm: khác null nghĩa là vụ mùa đã bị admin xóa
  private Instant deletedAt;

  // Optimistic lock: concurrent writers get a 409 instead of silently overwriting each other
  @Version
  @Column(nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
  private Long version;
}
