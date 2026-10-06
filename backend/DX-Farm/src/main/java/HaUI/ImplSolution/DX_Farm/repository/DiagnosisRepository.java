package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

  Optional<Diagnosis> findByDiseaseReportId(Long diseaseReportId);

  @Query("select d.id from Diagnosis d where d.diseaseReport.cropSeason.parcel.id in :parcelIds")
  List<Long> findIdsByParcelIds(@Param("parcelIds") Collection<Long> parcelIds);
}
