// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.DiseaseReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

  Optional<DiseaseReport> findByClientRequestId(UUID clientRequestId);

  List<DiseaseReport> findByRetakeOfIdAndDeletedAtIsNullOrderByIdAsc(Long reportId);

  List<DiseaseReport> findByRetakeOfIdInAndDeletedAtIsNullOrderByIdAsc(List<Long> reportIds);
}
