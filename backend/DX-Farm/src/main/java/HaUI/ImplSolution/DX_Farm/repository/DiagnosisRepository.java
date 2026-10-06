package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

  Optional<Diagnosis> findTopByDiseaseReportIdOrderByIdDesc(Long diseaseReportId);

  List<Diagnosis> findByDiseaseReportIdOrderByIdDesc(Long reportId);

  @Query(
  // @formatter:off
      """
      SELECT d
      FROM Diagnosis d
      JOIN FETCH d.disease
      LEFT JOIN FETCH d.treatmentRule r
      LEFT JOIN FETCH r.pesticide
      WHERE d.diseaseReport.id IN :reportIds
        AND d.id = (
          SELECT MAX(latest.id)
          FROM Diagnosis latest
          WHERE latest.diseaseReport.id = d.diseaseReport.id
        )
      """)
  // @formatter:on
  List<Diagnosis> findLatestForReports(@Param("reportIds") List<Long> reportIds);

  @Query("select d.id from Diagnosis d where d.diseaseReport.cropSeason.parcel.id in :parcelIds")
  List<Long> findIdsByParcelIds(@Param("parcelIds") Collection<Long> parcelIds);
}
