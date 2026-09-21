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

  // Approved diagnoses with a treatment rule that nobody has recorded spraying for yet.
  @Query("select count(d) from Diagnosis d where d.diseaseReport.cropSeason.id = :seasonId"
      + " and d.diseaseReport.deletedAt is null and d.approvalStatus = HaUI.ImplSolution.DX_Farm.model.ApprovalStatus.APPROVED"
      + " and d.treatmentRule is not null and not exists (select 1 from TreatmentApplication a"
      + " where a.diagnosis = d and a.confirmationStatus <> HaUI.ImplSolution.DX_Farm.model.TreatmentConfirmationStatus.REJECTED)")
  long countUntreatedApproved(@Param("seasonId") Long seasonId);

  @Query("select d.id from Diagnosis d where d.diseaseReport.cropSeason.parcel.id in :parcelIds")
  List<Long> findIdsByParcelIds(@Param("parcelIds") Collection<Long> parcelIds);
}
