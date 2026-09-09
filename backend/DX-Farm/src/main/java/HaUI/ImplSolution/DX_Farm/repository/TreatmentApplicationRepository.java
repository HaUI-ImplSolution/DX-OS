package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.TreatmentApplication;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentApplicationRepository extends JpaRepository<TreatmentApplication, Long> {

  Optional<TreatmentApplication> findTopByCropSeasonIdOrderByHarvestLockedUntilDesc(
      Long cropSeasonId);

  Optional<TreatmentApplication> findByDiagnosisId(Long diagnosisId);
}
