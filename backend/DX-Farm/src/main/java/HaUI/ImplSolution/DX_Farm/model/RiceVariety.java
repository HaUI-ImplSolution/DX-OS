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
@Table(name = "rice_varieties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiceVariety {

  // Primary key
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Variety code (e.g. OM5451), chosen when a season is created
  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String name;

  // Days from sowing to harvest, shortest and longest
  @Column(nullable = false)
  private int growthDaysMin;

  @Column(nullable = false)
  private int growthDaysMax;

  // Source of the figures
  @Column(nullable = false)
  private String source;

  // APPROVED: an agronomist confirmed the figures; IN_REVIEW: not confirmed yet
  @Column(nullable = false)
  private String reviewStatus;

  // A retired variety is not offered for new seasons
  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;
}
