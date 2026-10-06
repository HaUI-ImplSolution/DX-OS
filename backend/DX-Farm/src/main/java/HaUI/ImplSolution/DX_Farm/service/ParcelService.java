package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.identity.Household;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdRepository;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowActor;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowCase;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowService;
import HaUI.ImplSolution.DX_Farm.core.workflow.WorkflowStatus;
import HaUI.ImplSolution.DX_Farm.dto.ParcelCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.ParcelResponse;
import HaUI.ImplSolution.DX_Farm.dto.ParcelUpdateRequest;
import HaUI.ImplSolution.DX_Farm.event.ParcelCreatedEvent;
import HaUI.ImplSolution.DX_Farm.exception.AccountNotLinkedException;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.ApprovalStatus;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.model.ParcelEditRequest;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.ParcelEditRequestRepository;
import HaUI.ImplSolution.DX_Farm.repository.ParcelRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ParcelService {

  private final ParcelRepository parcelRepository;
  private final HouseholdRepository householdRepository;
  private final CropSeasonRepository cropSeasonRepository;
  private final ParcelEditRequestRepository editRequestRepository;
  private final CurrentUserService currentUserService;
  private final WorkflowService workflowService;
  private final ParcelCodeGenerator codeGenerator;
  private final GpsFixValidator gpsFixValidator;
  private final PendingRequestLimiter pendingRequestLimiter;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public ParcelService(ParcelRepository parcelRepository, HouseholdRepository householdRepository,
      CropSeasonRepository cropSeasonRepository, ParcelEditRequestRepository editRequestRepository,
      CurrentUserService currentUserService, WorkflowService workflowService,
      ParcelCodeGenerator codeGenerator, GpsFixValidator gpsFixValidator,
      PendingRequestLimiter pendingRequestLimiter, ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.parcelRepository = parcelRepository;
    this.householdRepository = householdRepository;
    this.cropSeasonRepository = cropSeasonRepository;
    this.editRequestRepository = editRequestRepository;
    this.currentUserService = currentUserService;
    this.workflowService = workflowService;
    this.codeGenerator = codeGenerator;
    this.gpsFixValidator = gpsFixValidator;
    this.pendingRequestLimiter = pendingRequestLimiter;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public List<ParcelResponse> listAll(boolean includeDeleted) {
    if (includeDeleted && !currentUserService.isAdmin()) {
      throw new AccessDeniedException("includeDeleted is limited to ADMIN");
    }
    List<Parcel> parcels;
    if (currentUserService.isFarmer()) {
      parcels = currentUserService.currentHousehold()
          .map(household -> parcelRepository
              .findByHousehold_IdAndDeletedAtIsNullOrderByIdAsc(household.getId()))
          .orElse(List.of());
    } else if (includeDeleted) {
      parcels = parcelRepository.findAllByOrderByIdAsc();
    } else {
      parcels = parcelRepository.findByDeletedAtIsNullOrderByIdAsc();
    }
    return parcels.stream().map(ParcelService::toResponse).toList();
  }

  public ParcelResponse getByCode(String code) {
    return toResponse(findParcelOrThrow(code));
  }

  // A farmer's parcel waits for an administrator; a parcel an administrator registers is approved
  // at once. The code is generated here, never taken from the client.
  @Transactional
  public ParcelResponse create(ParcelCreateRequest request, UUID clientRequestId) {
    Household household = resolveOwner(request.householdCode());
    if (clientRequestId != null) {
      var existing = parcelRepository.findByClientRequestId(clientRequestId);
      if (existing.isPresent()) {
        return toResponse(requireSameHousehold(existing.get(), household));
      }
    }
    boolean farmer = currentUserService.isFarmer();
    if (farmer) {
      gpsFixValidator.requireTrusted(request.gpsAccuracyMeters(), request.mockedLocation());
      pendingRequestLimiter.requireCapacity();
    }
    Parcel parcel = Parcel.builder().code(codeGenerator.next(household)).household(household)
        .farmerName(household.getFullName()).areaHectares(request.areaHectares())
        .location(request.location()).latitude(request.latitude()).longitude(request.longitude())
        .gpsAccuracyMeters(request.gpsAccuracyMeters()).approvalStatus(ApprovalStatus.PENDING)
        .clientRequestId(clientRequestId).build();
    Parcel saved;
    try {
      saved = parcelRepository.saveAndFlush(parcel);
    } catch (DataIntegrityViolationException e) {
      throw new InvalidStateException(clientRequestId == null ? "Parcel code already exists"
          : "Idempotency-Key is already being processed");
    }
    WorkflowCase workflowCase = workflowService.createCase(ApprovalCaseTypes.PARCEL_APPROVAL,
        ApprovalCaseTypes.PARCEL_ENTITY, saved.getId(), currentActor());
    return toResponse(settle(saved, workflowCase, farmer, false));
  }

  // A rejected parcel goes back to the administrator with the corrected values.
  @Transactional
  public ParcelResponse resubmit(Long id, ParcelUpdateRequest request) {
    Parcel parcel = findAccessibleByIdOrThrow(id);
    if (parcel.getApprovalStatus() != ApprovalStatus.REJECTED) {
      throw new InvalidStateException("Only a rejected parcel can be sent again: " + id);
    }
    boolean farmer = currentUserService.isFarmer();
    if (farmer) {
      gpsFixValidator.requireTrusted(request.gpsAccuracyMeters(), request.mockedLocation());
      pendingRequestLimiter.requireCapacity();
    }
    parcel.setAreaHectares(request.areaHectares());
    parcel.setLocation(request.location());
    parcel.setLatitude(request.latitude());
    parcel.setLongitude(request.longitude());
    parcel.setGpsAccuracyMeters(request.gpsAccuracyMeters());
    parcel.setLocationMissing(false);
    parcel.setApprovalStatus(ApprovalStatus.PENDING);
    parcel.setRejectionReason(null);
    Parcel saved = parcelRepository.save(parcel);
    WorkflowCase workflowCase = workflowService.recordTransition(ApprovalCaseTypes.PARCEL_APPROVAL,
        ApprovalCaseTypes.PARCEL_ENTITY, saved.getId(), WorkflowStatus.PENDING, currentActor(),
        null, null);
    return toResponse(settle(saved, workflowCase, farmer, true));
  }

  // An approved parcel is never edited in place: the change is a request an administrator decides.
  @Transactional
  public ParcelResponse requestEdit(Long id, ParcelUpdateRequest request) {
    Parcel parcel = findAccessibleByIdOrThrow(id);
    if (parcel.getApprovalStatus() != ApprovalStatus.APPROVED) {
      throw new InvalidStateException("Only an approved parcel can be changed by request: " + id);
    }
    List<Long> editIds = editRequestRepository.findByParcel_IdIn(List.of(id)).stream()
        .map(ParcelEditRequest::getId).toList();
    if (workflowService.hasPending(ApprovalCaseTypes.PARCEL_EDIT_REQUEST,
        ApprovalCaseTypes.PARCEL_EDIT_ENTITY, editIds)) {
      throw new InvalidStateException("Parcel already has a change request waiting: " + id);
    }
    boolean farmer = currentUserService.isFarmer();
    if (farmer) {
      gpsFixValidator.requireTrusted(request.gpsAccuracyMeters(), request.mockedLocation());
      pendingRequestLimiter.requireCapacity();
    }
    ParcelEditRequest edit = editRequestRepository.save(ParcelEditRequest.builder().parcel(parcel)
        .areaHectares(request.areaHectares()).location(request.location())
        .latitude(request.latitude()).longitude(request.longitude())
        .gpsAccuracyMeters(request.gpsAccuracyMeters()).reason(request.reason())
        .requestedByUserId(currentUserService.currentUserId()).build());
    Map<String, Object> proposed = new LinkedHashMap<>();
    proposed.put("parcelCode", parcel.getCode());
    proposed.put("areaHectares", edit.getAreaHectares());
    proposed.put("location", edit.getLocation());
    proposed.put("latitude", edit.getLatitude());
    proposed.put("longitude", edit.getLongitude());
    proposed.put("gpsAccuracyMeters", edit.getGpsAccuracyMeters());
    proposed.put("reason", edit.getReason());
    WorkflowCase workflowCase = workflowService.createCase(ApprovalCaseTypes.PARCEL_EDIT_REQUEST,
        ApprovalCaseTypes.PARCEL_EDIT_ENTITY, edit.getId(), currentActor(), proposed);
    if (!farmer) {
      workflowService.decide(workflowCase.getId(), WorkflowStatus.APPROVED, currentActor(), null);
    }
    return toResponse(parcel);
  }

  // Farmer requests wait and notify the administrators; an administrator's own are approved now.
  private Parcel settle(Parcel parcel, WorkflowCase workflowCase, boolean farmer,
      boolean resubmitted) {
    if (!farmer) {
      workflowService.decide(workflowCase.getId(), WorkflowStatus.APPROVED, currentActor(), null);
      return parcelRepository.findById(parcel.getId()).orElse(parcel);
    }
    eventPublisher.publishEvent(new ParcelCreatedEvent(parcel.getCode(),
        parcel.getHousehold().getCode(), parcel.getFarmerName(), parcel.getAreaHectares(),
        parcel.getLatitude(), parcel.getLongitude(), resubmitted));
    return parcel;
  }

  private WorkflowActor currentActor() {
    return new WorkflowActor(currentUserService.currentUserId(),
        currentUserService.currentFullName());
  }

  private static Parcel requireSameHousehold(Parcel parcel, Household household) {
    if (!parcel.getHousehold().getId().equals(household.getId())) {
      throw new InvalidStateException("Idempotency-Key was used for a different request");
    }
    return parcel;
  }

  @Transactional
  public void delete(Long id) {
    Parcel parcel = parcelRepository.findById(id).filter(p -> p.getDeletedAt() == null)
        .orElseThrow(() -> new ResourceNotFoundException("Parcel not found: " + id));
    if (cropSeasonRepository.existsByParcel_IdAndDeletedAtIsNull(id)) {
      throw new InvalidStateException("Parcel still has crop seasons, delete them first: " + id);
    }
    parcel.setDeletedAt(Instant.now(clock));
    parcelRepository.save(parcel);
  }

  // A farmer always registers parcels under their own household; ADMIN names the household.
  private Household resolveOwner(String householdCode) {
    if (currentUserService.isFarmer()) {
      return currentUserService.currentHousehold().orElseThrow(() -> new AccountNotLinkedException(
          "This account is not linked to a farm household, contact an administrator"));
    }
    if (householdCode == null || householdCode.isBlank()) {
      throw new IllegalArgumentException("householdCode is required");
    }
    return householdRepository.findByCode(householdCode)
        .orElseThrow(() -> new ResourceNotFoundException("Household not found: " + householdCode));
  }

  private Parcel findAccessibleByIdOrThrow(Long id) {
    return parcelRepository.findById(id).filter(parcel -> parcel.getDeletedAt() == null)
        .filter(currentUserService::canAccess)
        .orElseThrow(() -> new ResourceNotFoundException("Parcel not found: " + id));
  }

  private Parcel findParcelOrThrow(String code) {
    return parcelRepository.findByCodeAndDeletedAtIsNull(code).filter(currentUserService::canAccess)
        .orElseThrow(() -> new ResourceNotFoundException("Parcel not found: " + code));
  }

  static ParcelResponse toResponse(Parcel parcel) {
    return new ParcelResponse(parcel.getId(), parcel.getCode(), parcel.getHousehold().getCode(),
        parcel.getFarmerName(), parcel.getAreaHectares(), parcel.getLocation(),
        parcel.getLatitude(), parcel.getLongitude(), parcel.getGpsAccuracyMeters(),
        parcel.isLocationMissing(), parcel.getApprovalStatus().name(), parcel.getRejectionReason(),
        parcel.getCreatedAt(), parcel.getUpdatedAt());
  }
}
