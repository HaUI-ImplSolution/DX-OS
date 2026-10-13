// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummary;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummaryProvider;
import HaUI.ImplSolution.dxos.farm.model.CropSeason;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CropSeasonCaseSummaryProvider implements CaseSummaryProvider {

  private final CropSeasonRepository cropSeasonRepository;
  private final ParcelRepository parcelRepository;

  public CropSeasonCaseSummaryProvider(CropSeasonRepository cropSeasonRepository,
      ParcelRepository parcelRepository) {
    this.cropSeasonRepository = cropSeasonRepository;
    this.parcelRepository = parcelRepository;
  }

  @Override
  public String entityType() {
    return ApprovalCaseTypes.CROP_SEASON_ENTITY;
  }

  @Override
  public Map<Long, CaseSummary> summarize(Collection<Long> entityIds) {
    return cropSeasonRepository.findByIdIn(entityIds).stream()
        .collect(Collectors.toMap(CropSeason::getId, this::summary));
  }

  @Override
  public List<Long> entityIdsOfWorkspace(Long workspaceId) {
    List<Long> parcelIds =
        parcelRepository.findByHouseholdId(workspaceId).stream().map(Parcel::getId).toList();
    if (parcelIds.isEmpty()) {
      return List.of();
    }
    return cropSeasonRepository.findByParcel_IdIn(parcelIds).stream().map(CropSeason::getId)
        .toList();
  }

  private CaseSummary summary(CropSeason season) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("parcelCode", season.getParcel().getCode());
    details.put("riceVariety", season.getRiceVariety());
    details.put("seasonType",
        season.getSeasonType() != null ? season.getSeasonType().name() : null);
    details.put("startDate", season.getStartDate());
    details.put("expectedHarvestDate", season.getExpectedHarvestDate());
    details.put("reasonForOverride", season.getReasonForOverride());
    details.put("rejectionReason", season.getRejectionReason());
    String title = season.getCode() != null ? season.getCode() : "#" + season.getId();
    return new CaseSummary(title, season.getParcel().getHouseholdId(),
        season.getParcel().getFarmerName(), details);
  }
}
