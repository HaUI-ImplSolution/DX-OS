package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.DiagnosisJob;
import HaUI.ImplSolution.DX_Farm.model.DiagnosisJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisJobRepository extends JpaRepository<DiagnosisJob, String> {

  Optional<DiagnosisJob> findByDiseaseReportId(Long diseaseReportId);

  List<DiagnosisJob> findByStatusIn(Collection<DiagnosisJobStatus> statuses);
}
