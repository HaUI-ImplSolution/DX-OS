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

// A farmer's proposed change to an approved parcel; applied to the parcel only once approved.
@Entity
@Table(name = "parcel_edit_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParcelEditRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parcel_id", nullable = false)
  private Parcel parcel;

  // Proposed values
  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal areaHectares;

  private String location;

  @Column(nullable = false, precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(precision = 7, scale = 2)
  private BigDecimal gpsAccuracyMeters;

  // Why the farmer wants the change
  private String reason;

  // Token "sub" of the requester
  private String requestedByUserId;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;
}
