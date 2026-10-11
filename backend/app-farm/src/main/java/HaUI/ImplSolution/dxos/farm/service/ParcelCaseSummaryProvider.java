// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummary;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummaryProvider;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ParcelCaseSummaryProvider implements CaseSummaryProvider {

  private final ParcelRepository parcelRepository;

  public ParcelCaseSummaryProvider(ParcelRepository parcelRepository) {
    this.parcelRepository = parcelRepository;
  }

  @Override
  public String entityType() {
    return ApprovalCaseTypes.PARCEL_ENTITY;
  }

  @Override
  public Map<Long, CaseSummary> summarize(Collection<Long> entityIds) {
    return parcelRepository.findByIdIn(entityIds).stream()
        .collect(Collectors.toMap(Parcel::getId, this::summary));
  }

  @Override
  public List<Long> entityIdsOfWorkspace(Long workspaceId) {
    return parcelRepository.findByHouseholdId(workspaceId).stream().map(Parcel::getId).toList();
  }

  private CaseSummary summary(Parcel parcel) {
    Map<String, Object> details = new LinkedHashMap<>(ParcelDetails.of(parcel));
    details.put("rejectionReason", parcel.getRejectionReason());
    return new CaseSummary(parcel.getCode(), parcel.getHouseholdId(), parcel.getFarmerName(),
        details);
  }
}
