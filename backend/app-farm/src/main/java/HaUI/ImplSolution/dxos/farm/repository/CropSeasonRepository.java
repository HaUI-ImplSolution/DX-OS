// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.CropSeason;
import HaUI.ImplSolution.dxos.farm.model.CropSeasonStatus;
import HaUI.ImplSolution.dxos.farm.model.ApprovalStatus;
import HaUI.ImplSolution.dxos.farm.model.SeasonType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropSeasonRepository extends JpaRepository<CropSeason, Long> {

  List<CropSeason> findByParcel_CodeAndDeletedAtIsNullOrderByIdAsc(String parcelCode);

  List<CropSeason> findByParcel_CodeOrderByIdAsc(String parcelCode);

  // A rejected season is a draft and holds no slot on the parcel.
  boolean existsByParcel_IdAndStatusAndDeletedAtIsNullAndApprovalStatusNotAndIdNot(Long parcelId,
      CropSeasonStatus status, ApprovalStatus excluded, Long ignoredId);

  boolean existsByParcel_IdAndSeasonYearAndSeasonTypeAndDeletedAtIsNullAndApprovalStatusNotAndIdNot(
      Long parcelId, Integer seasonYear, SeasonType seasonType, ApprovalStatus excluded,
      Long ignoredId);

  Optional<CropSeason> findByClientRequestId(UUID clientRequestId);

  List<CropSeason> findByParcel_IdIn(Collection<Long> parcelIds);

  List<CropSeason> findByIdIn(Collection<Long> ids);

  boolean existsByParcel_IdAndDeletedAtIsNull(Long parcelId);

  // Approved seasons still growing whose expected harvest date has come.
  List<CropSeason> findByStatusAndApprovalStatusAndDeletedAtIsNullAndExpectedHarvestDateLessThanEqual(
      CropSeasonStatus status, ApprovalStatus approvalStatus, LocalDate date);
}
