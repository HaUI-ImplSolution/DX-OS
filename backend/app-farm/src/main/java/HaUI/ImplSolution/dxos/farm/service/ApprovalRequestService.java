// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseQuery;
import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowStatus;
import HaUI.ImplSolution.dxos.farm.dto.ApprovalRequestDetailResponse;
import HaUI.ImplSolution.dxos.farm.dto.ApprovalRequestResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.farm.model.CropSeason;
import HaUI.ImplSolution.dxos.farm.model.Diagnosis;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.model.ParcelEditRequest;
import HaUI.ImplSolution.dxos.farm.model.TreatmentApplication;
import HaUI.ImplSolution.dxos.farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelEditRequestRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import HaUI.ImplSolution.dxos.farm.repository.TreatmentApplicationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Read model for the management pages: workflow cases enriched with the parcel, season or
// diagnosis behind them and filterable by household and date.
@Service
@Transactional(readOnly = true)
public class ApprovalRequestService {

  private final WorkflowOperations workflowService;
  private final ParcelRepository parcelRepository;
  private final CropSeasonRepository cropSeasonRepository;
  private final ParcelEditRequestRepository editRequestRepository;
  private final DiagnosisRepository diagnosisRepository;
  private final TreatmentApplicationRepository treatmentApplicationRepository;
  private final ObjectStorage storageService;
  private final WorkspaceDirectory workspaceDirectory;
  private final Clock clock;

  public ApprovalRequestService(WorkflowOperations workflowService,
      ParcelRepository parcelRepository, CropSeasonRepository cropSeasonRepository,
      ParcelEditRequestRepository editRequestRepository, DiagnosisRepository diagnosisRepository,
      TreatmentApplicationRepository treatmentApplicationRepository, ObjectStorage storageService,
      WorkspaceDirectory workspaceDirectory, Clock clock) {
    this.workflowService = workflowService;
    this.parcelRepository = parcelRepository;
    this.cropSeasonRepository = cropSeasonRepository;
    this.editRequestRepository = editRequestRepository;
    this.diagnosisRepository = diagnosisRepository;
    this.treatmentApplicationRepository = treatmentApplicationRepository;
    this.storageService = storageService;
    this.workspaceDirectory = workspaceDirectory;
    this.clock = clock;
  }

  // assignedTo: a user id; unassigned: only cases nobody has taken. from/to are inclusive days.
  public PageResponse<ApprovalRequestResponse> list(String caseType, WorkflowStatus status,
      String householdCode, String assignedTo, boolean unassigned, LocalDate from, LocalDate to,
      int page, int size) {
    Instant start = from != null ? from.atStartOfDay(clock.getZone()).toInstant() : null;
    Instant end = to != null ? to.plusDays(1).atStartOfDay(clock.getZone()).toInstant() : null;
    List<CaseQuery.EntityIds> entities = null;
    if (householdCode != null && !householdCode.isBlank()) {
      entities = householdEntities(householdCode.strip());
    }
    var cases = workflowService.search(
        new CaseQuery(caseType, status, assignedTo, unassigned, start, end, entities), page, size);
    Map<Long, ApprovalRequestResponse> byCase = toResponses(cases.items());
    return cases.map(workflowCase -> byCase.get(workflowCase.id()));
  }

  public ApprovalRequestDetailResponse detail(Long caseId) {
    CaseView workflowCase = workflowService.get(caseId);
    ApprovalRequestResponse request = toResponses(List.of(workflowCase)).get(caseId);
    return new ApprovalRequestDetailResponse(request, workflowService.events(caseId));
  }

  // Entities that belong to a parcel of the household; none match an unknown household.
  private List<CaseQuery.EntityIds> householdEntities(String householdCode) {
    Long householdId =
        workspaceDirectory.findByCode(householdCode).map(WorkspaceView::id).orElse(null);
    List<Long> parcelIds = householdId == null ? List.<Long>of()
        : parcelRepository.findByHouseholdId(householdId).stream().map(Parcel::getId).toList();
    if (parcelIds.isEmpty()) {
      return List.of();
    }
    List<Long> seasonIds =
        cropSeasonRepository.findByParcel_IdIn(parcelIds).stream().map(CropSeason::getId).toList();
    List<Long> editIds = editRequestRepository.findByParcel_IdIn(parcelIds).stream()
        .map(ParcelEditRequest::getId).toList();
    List<Long> diagnosisIds = diagnosisRepository.findIdsByParcelIds(parcelIds);
    List<Long> treatmentIds = treatmentApplicationRepository.findIdsByParcelIds(parcelIds);
    return List.of(new CaseQuery.EntityIds(ApprovalCaseTypes.PARCEL_ENTITY, parcelIds),
        new CaseQuery.EntityIds(ApprovalCaseTypes.CROP_SEASON_ENTITY, seasonIds),
        new CaseQuery.EntityIds(ApprovalCaseTypes.PARCEL_EDIT_ENTITY, editIds),
        new CaseQuery.EntityIds(ApprovalCaseTypes.DIAGNOSIS_ENTITY, diagnosisIds),
        new CaseQuery.EntityIds(ApprovalCaseTypes.TREATMENT_ENTITY, treatmentIds));
  }

  // Loads the entities in one query per type.
  private Map<Long, ApprovalRequestResponse> toResponses(List<CaseView> cases) {
    Map<String,List<Long>>idsByType=cases.stream().collect(Collectors.groupingBy(CaseView::entityType,Collectors.mapping(CaseView::entityId,Collectors.toList())));Map<Long,Parcel>parcels=byId(parcelRepository.findByIdIn(idsByType.getOrDefault(ApprovalCaseTypes.PARCEL_ENTITY,List.of())),Parcel::getId);Map<Long,CropSeason>seasons=byId(cropSeasonRepository.findByIdIn(idsByType.getOrDefault(ApprovalCaseTypes.CROP_SEASON_ENTITY,List.of())),CropSeason::getId);Map<Long,ParcelEditRequest>edits=byId(editRequestRepository.findAllById(idsByType.getOrDefault(ApprovalCaseTypes.PARCEL_EDIT_ENTITY,List.of())),ParcelEditRequest::getId);Map<Long,Diagnosis>diagnoses=byId(diagnosisRepository.findAllById(idsByType.getOrDefault(ApprovalCaseTypes.DIAGNOSIS_ENTITY,List.of())),Diagnosis::getId);Map<Long,TreatmentApplication>treatments=byId(treatmentApplicationRepository.findAllById(idsByType.getOrDefault(ApprovalCaseTypes.TREATMENT_ENTITY,List.of())),TreatmentApplication::getId);

    Set<Long> householdIds = new HashSet<>();
    parcels.values().forEach(parcel -> householdIds.add(parcel.getHouseholdId()));
    seasons.values().forEach(season -> householdIds.add(season.getParcel().getHouseholdId()));
    edits.values().forEach(edit -> householdIds.add(edit.getParcel().getHouseholdId()));
    diagnoses.values().forEach(diagnosis -> householdIds
        .add(diagnosis.getDiseaseReport().getCropSeason().getParcel().getHouseholdId()));
    treatments.values().forEach(application -> householdIds
        .add(application.getCropSeason().getParcel().getHouseholdId()));
    Map<Long, WorkspaceView> owners = workspaceDirectory.findByIds(householdIds);

    Map<Long,ApprovalRequestResponse>result=new HashMap<>();for(CaseView workflowCase:cases){Long entityId=workflowCase.entityId();ApprovalRequestResponse response=switch(workflowCase.entityType()){case ApprovalCaseTypes.PARCEL_ENTITY->parcelRequest(workflowCase,parcels.get(entityId),owners);case ApprovalCaseTypes.CROP_SEASON_ENTITY->seasonRequest(workflowCase,seasons.get(entityId),owners);case ApprovalCaseTypes.PARCEL_EDIT_ENTITY->editRequest(workflowCase,edits.get(entityId),owners);case ApprovalCaseTypes.DIAGNOSIS_ENTITY->diagnosisRequest(workflowCase,diagnoses.get(entityId),owners);case ApprovalCaseTypes.TREATMENT_ENTITY->treatmentRequest(workflowCase,treatments.get(entityId),owners);default->build(workflowCase,"#"+entityId,null,null,Map.of());};result.put(workflowCase.id(),response);}return result;
  }

  private ApprovalRequestResponse parcelRequest(CaseView workflowCase, Parcel parcel,
      Map<Long, WorkspaceView> owners) {
    if (parcel == null) {
      return build(workflowCase, "#" + workflowCase.entityId(), null, null, Map.of());
    }
    Map<String, Object> details = new LinkedHashMap<>(parcelValues(parcel));
    details.put("rejectionReason", parcel.getRejectionReason());
    return build(workflowCase, parcel.getCode(), owners.get(parcel.getHouseholdId()),
        parcel.getFarmerName(), details);
  }

  private ApprovalRequestResponse seasonRequest(CaseView workflowCase, CropSeason season,
      Map<Long, WorkspaceView> owners) {
    if (season == null) {
      return build(workflowCase, "#" + workflowCase.entityId(), null, null, Map.of());
    }
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
    return build(workflowCase, title, owners.get(season.getParcel().getHouseholdId()),
        season.getParcel().getFarmerName(), details);
  }

  private ApprovalRequestResponse editRequest(CaseView workflowCase, ParcelEditRequest edit,
      Map<Long, WorkspaceView> owners) {
    if (edit == null) {
      return build(workflowCase, "#" + workflowCase.entityId(), null, null, Map.of());
    }
    Parcel parcel = edit.getParcel();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("reason", edit.getReason());
    parcelValues(parcel).forEach((key, value) -> details.put("current." + key, value));
    details.put("proposed.areaHectares", edit.getAreaHectares());
    details.put("proposed.location", edit.getLocation());
    details.put("proposed.latitude", edit.getLatitude());
    details.put("proposed.longitude", edit.getLongitude());
    details.put("proposed.gpsAccuracyMeters", edit.getGpsAccuracyMeters());
    return build(workflowCase, parcel.getCode(), owners.get(parcel.getHouseholdId()),
        parcel.getFarmerName(), details);
  }

  private ApprovalRequestResponse diagnosisRequest(CaseView workflowCase, Diagnosis diagnosis,
      Map<Long, WorkspaceView> owners) {
    if (diagnosis == null) {
      return build(workflowCase, "#" + workflowCase.entityId(), null, null, Map.of());
    }
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
    details.put("imageUrl", storageService.presignedUrl(report.getImageRef()));
    return build(workflowCase, "Báo cáo #" + report.getId() + " · " + parcel.getCode(),
        owners.get(parcel.getHouseholdId()), parcel.getFarmerName(), details);
  }

  private ApprovalRequestResponse treatmentRequest(CaseView workflowCase,
      TreatmentApplication application, Map<Long, WorkspaceView> owners) {
    if (application == null) {
      return build(workflowCase, "#" + workflowCase.entityId(), null, null, Map.of());
    }
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
    return build(workflowCase, title, owners.get(parcel.getHouseholdId()), parcel.getFarmerName(),
        details);
  }

  private static Map<String, Object> parcelValues(Parcel parcel) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("areaHectares", parcel.getAreaHectares());
    values.put("location", parcel.getLocation());
    values.put("latitude", parcel.getLatitude());
    values.put("longitude", parcel.getLongitude());
    values.put("gpsAccuracyMeters", parcel.getGpsAccuracyMeters());
    return values;
  }

  private static ApprovalRequestResponse build(CaseView workflowCase, String title,
      WorkspaceView household, String farmerName, Map<String, Object> details) {
    return new ApprovalRequestResponse(workflowCase.id(), workflowCase.caseType(),
        workflowCase.status().name(), title, household != null ? household.code() : null,
        farmerName, new LinkedHashMap<>(details), workflowCase.createdAt(),
        workflowCase.assignedTo(), workflowCase.assignedToName(), workflowCase.updatedAt());
  }

  private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
    return new ArrayList<>(items).stream().collect(Collectors.toMap(id, Function.identity()));
  }
}
