// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.TreatmentApplication;
import HaUI.ImplSolution.dxos.farm.model.TreatmentConfirmationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TreatmentApplicationRepository extends JpaRepository<TreatmentApplication, Long> {

  // The isolation that binds a harvest: only an engineer-confirmed application counts.
  Optional<TreatmentApplication> findTopByCropSeasonIdAndConfirmationStatusOrderByHarvestLockedUntilDesc(
      Long cropSeasonId, TreatmentConfirmationStatus status);

  boolean existsByCropSeasonIdAndConfirmationStatus(Long cropSeasonId,
      TreatmentConfirmationStatus status);

  Optional<TreatmentApplication> findByDiagnosisId(Long diagnosisId);

  // Reports that have waited for an engineer since before the cutoff.
  List<TreatmentApplication> findByConfirmationStatusAndReportedAtBefore(
      TreatmentConfirmationStatus status, Instant cutoff);

  // A rejected report was never accepted, so it is not part of the spraying history.
  List<TreatmentApplication> findByCropSeasonIdAndConfirmationStatusNotOrderByAppliedAtDesc(
      Long cropSeasonId, TreatmentConfirmationStatus excluded);

  @Query("select a.id from TreatmentApplication a where a.cropSeason.parcel.id in :parcelIds")
  List<Long> findIdsByParcelIds(@Param("parcelIds") Collection<Long> parcelIds);
}
