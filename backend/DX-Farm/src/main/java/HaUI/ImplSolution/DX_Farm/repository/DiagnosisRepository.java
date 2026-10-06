package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

  Optional<Diagnosis> findTopByDiseaseReportIdOrderByIdDesc(Long diseaseReportId);

  List<Diagnosis> findByDiseaseReportIdOrderByIdDesc(Long reportId);

  @Query("select d from Diagnosis d join fetch d.disease left join fetch d.treatmentRule r left join fetch r.pesticide where d.diseaseReport.id in :reportIds and d.id = (select max(latest.id) from Diagnosis latest where latest.diseaseReport.id = d.diseaseReport.id)")
  List<Diagnosis> findLatestForReports(@Param("reportIds") List<Long> reportIds);
}
