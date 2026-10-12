// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummary;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummaryProvider;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.model.ParcelEditRequest;
import HaUI.ImplSolution.dxos.farm.repository.ParcelEditRequestRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ParcelEditCaseSummaryProvider implements CaseSummaryProvider {

  private final ParcelEditRequestRepository editRequestRepository;
  private final ParcelRepository parcelRepository;

  public ParcelEditCaseSummaryProvider(ParcelEditRequestRepository editRequestRepository,
      ParcelRepository parcelRepository) {
    this.editRequestRepository = editRequestRepository;
    this.parcelRepository = parcelRepository;
  }

  @Override
  public String entityType() {
    return ApprovalCaseTypes.PARCEL_EDIT_ENTITY;
  }

  @Override
  public Map<Long, CaseSummary> summarize(Collection<Long> entityIds) {
    return editRequestRepository.findAllById(entityIds).stream()
        .collect(Collectors.toMap(ParcelEditRequest::getId, this::summary));
  }

  @Override
  public List<Long> entityIdsOfWorkspace(Long workspaceId) {
    List<Long> parcelIds =
        parcelRepository.findByHouseholdId(workspaceId).stream().map(Parcel::getId).toList();
    if (parcelIds.isEmpty()) {
      return List.of();
    }
    return editRequestRepository.findByParcel_IdIn(parcelIds).stream().map(ParcelEditRequest::getId)
        .toList();
  }

  private CaseSummary summary(ParcelEditRequest edit) {
    Parcel parcel = edit.getParcel();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("reason", edit.getReason());
    ParcelDetails.of(parcel).forEach((key, value) -> details.put("current." + key, value));
    details.put("proposed.areaHectares", edit.getAreaHectares());
    details.put("proposed.location", edit.getLocation());
    details.put("proposed.latitude", edit.getLatitude());
    details.put("proposed.longitude", edit.getLongitude());
    details.put("proposed.gpsAccuracyMeters", edit.getGpsAccuracyMeters());
    return new CaseSummary(parcel.getCode(), parcel.getHouseholdId(), parcel.getFarmerName(),
        details);
  }
}
