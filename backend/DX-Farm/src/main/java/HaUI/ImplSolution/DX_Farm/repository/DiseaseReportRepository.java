package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.DiseaseReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiseaseReportRepository extends JpaRepository<DiseaseReport, Long> {

  List<DiseaseReport> findByCropSeasonId(Long cropSeasonId);

  Optional<DiseaseReport> findByClientRequestId(UUID clientRequestId);
}
