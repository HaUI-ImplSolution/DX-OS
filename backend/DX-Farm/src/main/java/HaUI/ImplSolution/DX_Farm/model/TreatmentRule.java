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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "treatment_rules",
    uniqueConstraints = @UniqueConstraint(columnNames = {"disease_id", "pesticide_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TreatmentRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "disease_id", nullable = false)
  private Disease disease;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pesticide_id", nullable = false)
  private Pesticide pesticide;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal doseMin;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal doseMax;

  @Column(nullable = false)
  private String doseUnit;

  @Column(nullable = false)
  private Integer phiDays;

  // Seed provenance and application guidance can legitimately exceed varchar(255).
  @Column(columnDefinition = "text")
  private String notes;

  private String source;

  private String version;

  // Rule mất eligibility vẫn có thể được giữ lại để bảo toàn lịch sử diagnosis.
  @Builder.Default
  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean active = true;

  public boolean allowsDose(BigDecimal actualDose) {
    return actualDose != null && actualDose.compareTo(doseMin) >= 0
        && actualDose.compareTo(doseMax) <= 0;
  }
}
