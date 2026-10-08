// SPDX-License-Identifier: Apache-2.0
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

  // Approved diagnoses with a treatment rule that nobody has recorded spraying for yet.
  @Query("select count(d) from Diagnosis d where d.diseaseReport.cropSeason.id = :seasonId"
      + " and d.diseaseReport.deletedAt is null and d.approvalStatus = HaUI.ImplSolution.DX_Farm.model.ApprovalStatus.APPROVED"
      + " and d.treatmentRule is not null and not exists (select 1 from TreatmentApplication a"
      + " where a.diagnosis = d and a.confirmationStatus <> HaUI.ImplSolution.DX_Farm.model.TreatmentConfirmationStatus.REJECTED)")
  long countUntreatedApproved(@Param("seasonId") Long seasonId);

  @Query("select d.id from Diagnosis d where d.diseaseReport.cropSeason.parcel.id in :parcelIds")
  List<Long> findIdsByParcelIds(@Param("parcelIds") Collection<Long> parcelIds);
}
