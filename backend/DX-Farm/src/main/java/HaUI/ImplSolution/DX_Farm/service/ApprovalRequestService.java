package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.identity.Household;
import HaUI.ImplSolution.DX_Farm.core.storage.StorageService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowCase;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.dto.ApprovalRequestDetailResponse;
import HaUI.ImplSolution.DX_Farm.dto.ApprovalRequestResponse;
import HaUI.ImplSolution.DX_Farm.dto.PageResponse;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.Diagnosis;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.model.ParcelEditRequest;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.DX_Farm.repository.ParcelEditRequestRepository;
import HaUI.ImplSolution.DX_Farm.repository.ParcelRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Read model for the management pages: workflow cases enriched with the parcel, season or
// diagnosis behind them and filterable by household and date.
@Service
@Transactional(readOnly = true)
public class ApprovalRequestService {

  private final WorkflowService workflowService;
  private final ParcelRepository parcelRepository;
  private final CropSeasonRepository cropSeasonRepository;
  private final ParcelEditRequestRepository editRequestRepository;
  private final DiagnosisRepository diagnosisRepository;
  private final StorageService storageService;
  private final Clock clock;

  public ApprovalRequestService(WorkflowService workflowService, ParcelRepository parcelRepository,
      CropSeasonRepository cropSeasonRepository, ParcelEditRequestRepository editRequestRepository,
      DiagnosisRepository diagnosisRepository, StorageService storageService, Clock clock) {
    this.workflowService = workflowService;
    this.parcelRepository = parcelRepository;
    this.cropSeasonRepository = cropSeasonRepository;
    this.editRequestRepository = editRequestRepository;
    this.diagnosisRepository = diagnosisRepository;
    this.storageService = storageService;
    this.clock = clock;
  }

  // assignedTo: a user id; unassigned: only cases nobody has taken. from/to are inclusive days.
  public PageResponse<ApprovalRequestResponse> list(String caseType, WorkflowStatus status,
      String householdCode, String assignedTo, boolean unassigned, LocalDate from, LocalDate to,
      int page, int size) {
    Specification<WorkflowCase> filter = Specification.unrestricted();
    if (caseType != null) {
      filter = filter.and((root, query, cb) -> cb.equal(root.get("caseType"), caseType));
    }
    if (status != null) {
      filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (assignedTo != null) {
      filter = filter.and((root, query, cb) -> cb.equal(root.get("assignedTo"), assignedTo));
    }
    if (unassigned) {
      filter = filter.and((root, query, cb) -> cb.isNull(root.get("assignedTo")));
    }
    if (from != null) {
      Instant start = from.atStartOfDay(clock.getZone()).toInstant();
      filter =
          filter.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
    }
    if (to != null) {
      Instant end = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
      filter = filter.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
    }
    if (householdCode != null && !householdCode.isBlank()) {
      filter = filter.and(householdFilter(householdCode.strip()));
    }
    var cases = workflowService.search(filter, page, size);
    Map<Long, ApprovalRequestResponse> byCase = toResponses(cases.getContent());
    return new PageResponse<>(
        cases.getContent().stream().map(workflowCase -> byCase.get(workflowCase.getId())).toList(),
        cases.getNumber(), cases.getSize(), cases.getTotalElements(), cases.getTotalPages());
  }

  public ApprovalRequestDetailResponse detail(Long caseId) {
    WorkflowCase workflowCase = workflowService.get(caseId);
    ApprovalRequestResponse request = toResponses(List.of(workflowCase)).get(caseId);
    return new ApprovalRequestDetailResponse(request, workflowService.events(caseId));
  }

  // Cases whose entity belongs to a parcel of the household; none match an unknown household.
  private Specification<WorkflowCase> householdFilter(String householdCode) {
    List<Long> parcelIds =
        parcelRepository.findByHousehold_Code(householdCode).stream().map(Parcel::getId).toList();
    if (parcelIds.isEmpty()) {
      return (root, query, cb) -> cb.disjunction();
    }
    List<Long> seasonIds =
        cropSeasonRepository.findByParcel_IdIn(parcelIds).stream().map(CropSeason::getId).toList();
    List<Long> editIds = editRequestRepository.findByParcel_IdIn(parcelIds).stream()
        .map(ParcelEditRequest::getId).toList();
    List<Long> diagnosisIds = diagnosisRepository.findIdsByParcelIds(parcelIds);
    Specification<WorkflowCase> spec = entityIn(ApprovalCaseTypes.PARCEL_ENTITY, parcelIds);
    spec = spec.or(entityIn(ApprovalCaseTypes.CROP_SEASON_ENTITY, seasonIds));
    spec = spec.or(entityIn(ApprovalCaseTypes.PARCEL_EDIT_ENTITY, editIds));
    return spec.or(entityIn(ApprovalCaseTypes.DIAGNOSIS_ENTITY, diagnosisIds));
  }

  private static Specification<WorkflowCase> entityIn(String entityType, Collection<Long> ids) {
    if (ids.isEmpty()) {
      return (root, query, cb) -> cb.disjunction();
    }
    return (root, query, cb) -> cb.and(cb.equal(root.get("entityType"), entityType),
        root.get("entityId").in(ids));
  }

  // Loads the entities in one query per type.
  private Map<Long, ApprovalRequestResponse> toResponses(List<WorkflowCase> cases) {
    Map<String,List<Long>>idsByType=cases.stream().collect(Collectors.groupingBy(WorkflowCase::getEntityType,Collectors.mapping(WorkflowCase::getEntityId,Collectors.toList())));Map<Long,Parcel>parcels=byId(parcelRepository.findByIdIn(idsByType.getOrDefault(ApprovalCaseTypes.PARCEL_ENTITY,List.of())),Parcel::getId);Map<Long,CropSeason>seasons=byId(cropSeasonRepository.findByIdIn(idsByType.getOrDefault(ApprovalCaseTypes.CROP_SEASON_ENTITY,List.of())),CropSeason::getId);Map<Long,ParcelEditRequest>edits=byId(editRequestRepository.findAllById(idsByType.getOrDefault(ApprovalCaseTypes.PARCEL_EDIT_ENTITY,List.of())),ParcelEditRequest::getId);Map<Long,Diagnosis>diagnoses=byId(diagnosisRepository.findAllById(idsByType.getOrDefault(ApprovalCaseTypes.DIAGNOSIS_ENTITY,List.of())),Diagnosis::getId);

    Map<Long,ApprovalRequestResponse>result=new HashMap<>();for(WorkflowCase workflowCase:cases){Long entityId=workflowCase.getEntityId();ApprovalRequestResponse response=switch(workflowCase.getEntityType()){case ApprovalCaseTypes.PARCEL_ENTITY->parcelRequest(workflowCase,parcels.get(entityId));case ApprovalCaseTypes.CROP_SEASON_ENTITY->seasonRequest(workflowCase,seasons.get(entityId));case ApprovalCaseTypes.PARCEL_EDIT_ENTITY->editRequest(workflowCase,edits.get(entityId));case ApprovalCaseTypes.DIAGNOSIS_ENTITY->diagnosisRequest(workflowCase,diagnoses.get(entityId));default->build(workflowCase,"#"+entityId,null,null,Map.of());};result.put(workflowCase.getId(),response);}return result;
  }

  private ApprovalRequestResponse parcelRequest(WorkflowCase workflowCase, Parcel parcel) {
    if (parcel == null) {
      return build(workflowCase, "#" + workflowCase.getEntityId(), null, null, Map.of());
    }
    Map<String, Object> details = new LinkedHashMap<>(parcelValues(parcel));
    details.put("rejectionReason", parcel.getRejectionReason());
    return build(workflowCase, parcel.getCode(), parcel.getHousehold(), parcel.getFarmerName(),
        details);
  }

  private ApprovalRequestResponse seasonRequest(WorkflowCase workflowCase, CropSeason season) {
    if (season == null) {
      return build(workflowCase, "#" + workflowCase.getEntityId(), null, null, Map.of());
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
    return build(workflowCase, title, season.getParcel().getHousehold(),
        season.getParcel().getFarmerName(), details);
  }

  private ApprovalRequestResponse editRequest(WorkflowCase workflowCase, ParcelEditRequest edit) {
    if (edit == null) {
      return build(workflowCase, "#" + workflowCase.getEntityId(), null, null, Map.of());
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
    return build(workflowCase, parcel.getCode(), parcel.getHousehold(), parcel.getFarmerName(),
        details);
  }

  private ApprovalRequestResponse diagnosisRequest(WorkflowCase workflowCase, Diagnosis diagnosis) {
    if (diagnosis == null) {
      return build(workflowCase, "#" + workflowCase.getEntityId(), null, null, Map.of());
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
        parcel.getHousehold(), parcel.getFarmerName(), details);
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

  private static ApprovalRequestResponse build(WorkflowCase workflowCase, String title,
      Household household, String farmerName, Map<String, Object> details) {
    return new ApprovalRequestResponse(workflowCase.getId(), workflowCase.getCaseType(),
        workflowCase.getStatus().name(), title, household != null ? household.getCode() : null,
        farmerName, new LinkedHashMap<>(details), workflowCase.getCreatedAt(),
        workflowCase.getAssignedTo(), workflowCase.getAssignedToName(),
        workflowCase.getUpdatedAt());
  }

  private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
    return new ArrayList<>(items).stream().collect(Collectors.toMap(id, Function.identity()));
  }
}
