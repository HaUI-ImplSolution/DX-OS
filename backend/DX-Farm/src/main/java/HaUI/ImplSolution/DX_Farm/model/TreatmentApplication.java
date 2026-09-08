package HaUI.ImplSolution.DX_Farm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "crop_season_id", nullable = false)
  private CropSeason cropSeason;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "diagnosis_id", unique = true)
  private Diagnosis diagnosis;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pesticide_id", nullable = false)
  private Pesticide pesticide;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal doseAmountSnapshot;

  @Column(nullable = false)
  private String doseUnitSnapshot;

  @Column(nullable = false)
  private Integer phiDaysSnapshot;

  @Column(nullable = false)
  private LocalDate appliedAt;

  @Column(nullable = false)
  private LocalDate harvestLockedUntil;
}
