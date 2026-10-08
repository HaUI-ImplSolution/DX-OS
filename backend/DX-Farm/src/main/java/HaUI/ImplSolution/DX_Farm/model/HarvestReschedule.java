// SPDX-License-Identifier: Apache-2.0
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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

// One change of the expected harvest date. Rows are only added, so the history of a season shows
// every date it went through.
@Entity
@Table(name = "harvest_reschedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HarvestReschedule {

  // Primary key
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "crop_season_id", nullable = false)
  private CropSeason cropSeason;

  @Column(nullable = false)
  private LocalDate previousDate;

  @Column(nullable = false)
  private LocalDate newDate;

  @Column(nullable = false)
  private String reason;

  // Token "sub" and display name of whoever changed the date
  private String rescheduledByUserId;

  private String rescheduledByName;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant rescheduledAt;
}
