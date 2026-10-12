// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummary;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummaryProvider;
import HaUI.ImplSolution.dxos.farm.model.Diagnosis;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DiagnosisCaseSummaryProvider implements CaseSummaryProvider {

  private final DiagnosisRepository diagnosisRepository;
  private final ParcelRepository parcelRepository;
  private final ObjectStorage objectStorage;

  public DiagnosisCaseSummaryProvider(DiagnosisRepository diagnosisRepository,
      ParcelRepository parcelRepository, ObjectStorage objectStorage) {
    this.diagnosisRepository = diagnosisRepository;
    this.parcelRepository = parcelRepository;
    this.objectStorage = objectStorage;
  }

  @Override
  public String entityType() {
    return ApprovalCaseTypes.DIAGNOSIS_ENTITY;
  }

  @Override
  public Map<Long, CaseSummary> summarize(Collection<Long> entityIds) {
    return diagnosisRepository.findAllById(entityIds).stream()
        .collect(Collectors.toMap(Diagnosis::getId, this::summary));
  }

  @Override
  public List<Long> entityIdsOfWorkspace(Long workspaceId) {
    List<Long> parcelIds =
        parcelRepository.findByHouseholdId(workspaceId).stream().map(Parcel::getId).toList();
    return parcelIds.isEmpty() ? List.of() : diagnosisRepository.findIdsByParcelIds(parcelIds);
  }

  private CaseSummary summary(Diagnosis diagnosis) {
    var report = diagnosis.getDiseaseReport();
    Parcel parcel = report.getCropSeason().getParcel();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("reportId", report.getId());
    details.put("parcelCode", parcel.getCode());
    details.put("diseaseCode", diagnosis.getDisease().getCode());
    details.put("diseaseName", diagnosis.getDisease().getName());
    details.put("confidence", diagnosis.getConfidence());
    details.put("hasTreatmentRule", diagnosis.getTreatmentRule() != null);
    details.put("recommendation", diagnosis.getRecommendationText());
    details.put("rejectionReason", diagnosis.getRejectionReason());
    details.put("imageUrl", objectStorage.presignedUrl(report.getImageRef()));
    return new CaseSummary("Báo cáo #" + report.getId() + " · " + parcel.getCode(),
        parcel.getHouseholdId(), parcel.getFarmerName(), details);
  }
}
