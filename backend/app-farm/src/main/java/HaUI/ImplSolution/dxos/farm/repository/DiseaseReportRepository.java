// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.DiseaseReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiseaseReportRepository extends JpaRepository<DiseaseReport, Long> {

  List<DiseaseReport> findByCropSeasonIdAndDeletedAtIsNullOrderByIdAsc(Long cropSeasonId);

  List<DiseaseReport> findByCropSeasonIdOrderByIdAsc(Long cropSeasonId);

  // Reports whose diagnosis has not been decided yet, or that have none.
  @Query("select count(r) from DiseaseReport r where r.cropSeason.id = :seasonId"
      + " and r.deletedAt is null and not exists (select 1 from Diagnosis d"
      + " where d.diseaseReport = r and d.approvalStatus <> HaUI.ImplSolution.dxos.farm.model.ApprovalStatus.PENDING)")
  long countAwaitingDecision(@Param("seasonId") Long seasonId);

  // Reports of active seasons that nobody has diagnosed yet, oldest first.
  @Query("select r from DiseaseReport r where r.deletedAt is null"
      + " and r.status = HaUI.ImplSolution.dxos.farm.model.DiseaseReportStatus.PENDING"
      + " and r.cropSeason.deletedAt is null and r.cropSeason.parcel.deletedAt is null"
      + " and r.cropSeason.status = HaUI.ImplSolution.dxos.farm.model.CropSeasonStatus.ACTIVE"
      + " order by r.reportedAt asc, r.id asc")
  Page<DiseaseReport> findAwaitingDiagnosis(Pageable pageable);

  Optional<DiseaseReport> findByClientRequestId(UUID clientRequestId);

  List<DiseaseReport> findByRetakeOfIdAndDeletedAtIsNullOrderByIdAsc(Long reportId);

  List<DiseaseReport> findByRetakeOfIdInAndDeletedAtIsNullOrderByIdAsc(List<Long> reportIds);
}
