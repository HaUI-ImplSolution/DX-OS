// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummary;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummaryProvider;
import HaUI.ImplSolution.dxos.farm.model.Diagnosis;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.model.TreatmentApplication;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import HaUI.ImplSolution.dxos.farm.repository.TreatmentApplicationRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TreatmentCaseSummaryProvider implements CaseSummaryProvider {

  private final TreatmentApplicationRepository treatmentApplicationRepository;
  private final ParcelRepository parcelRepository;

  public TreatmentCaseSummaryProvider(TreatmentApplicationRepository treatmentApplicationRepository,
      ParcelRepository parcelRepository) {
    this.treatmentApplicationRepository = treatmentApplicationRepository;
    this.parcelRepository = parcelRepository;
  }

  @Override
  public String entityType() {
    return ApprovalCaseTypes.TREATMENT_ENTITY;
  }

  @Override
  public Map<Long, CaseSummary> summarize(Collection<Long> entityIds) {
    return treatmentApplicationRepository.findAllById(entityIds).stream()
        .collect(Collectors.toMap(TreatmentApplication::getId, this::summary));
  }

  @Override
  public List<Long> entityIdsOfWorkspace(Long workspaceId) {
    List<Long> parcelIds =
        parcelRepository.findByHouseholdId(workspaceId).stream().map(Parcel::getId).toList();
    return parcelIds.isEmpty() ? List.of()
        : treatmentApplicationRepository.findIdsByParcelIds(parcelIds);
  }

  private CaseSummary summary(TreatmentApplication application) {
    Parcel parcel = application.getCropSeason().getParcel();
    Diagnosis diagnosis = application.getDiagnosis();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("parcelCode", parcel.getCode());
    details.put("seasonId", application.getCropSeason().getId());
    if (diagnosis != null) {
      details.put("reportId", diagnosis.getDiseaseReport().getId());
      details.put("diseaseName", diagnosis.getDisease().getName());
    }
    details.put("pesticide", application.getPesticide().getName());
    details.put("doseAmount", application.getDoseAmountSnapshot());
    details.put("doseUnit", application.getDoseUnitSnapshot());
    details.put("appliedAt", application.getAppliedAt());
    details.put("harvestLockedUntil", application.getHarvestLockedUntil());
    details.put("rejectionReason", application.getRejectionReason());
    String title = diagnosis != null
        ? "Phun thuốc · báo cáo #" + diagnosis.getDiseaseReport().getId() + " · " + parcel.getCode()
        : "Phun thuốc · " + parcel.getCode();
    return new CaseSummary(title, parcel.getHouseholdId(), parcel.getFarmerName(), details);
  }
}
