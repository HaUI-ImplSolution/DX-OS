// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowActor;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.PendingRequestLimit;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowStatus;
import HaUI.ImplSolution.dxos.farm.dto.CropSeasonCreateRequest;
import HaUI.ImplSolution.dxos.farm.dto.CropSeasonResponse;
import HaUI.ImplSolution.dxos.farm.dto.CropSeasonSuggestionResponse;
import HaUI.ImplSolution.dxos.farm.dto.HarvestReadinessResponse;
import HaUI.ImplSolution.dxos.farm.dto.HarvestRequest;
import HaUI.ImplSolution.dxos.farm.dto.HarvestRescheduleRequest;
import HaUI.ImplSolution.dxos.farm.dto.HarvestRescheduleResponse;
import HaUI.ImplSolution.dxos.farm.dto.HarvestWarning;
import HaUI.ImplSolution.dxos.farm.dto.IsolationStatusResponse;
import HaUI.ImplSolution.dxos.farm.event.CropSeasonCreatedEvent;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.farm.model.ApprovalStatus;
import HaUI.ImplSolution.dxos.farm.model.CropSeason;
import HaUI.ImplSolution.dxos.farm.model.CropSeasonStatus;
import HaUI.ImplSolution.dxos.farm.model.HarvestReschedule;
import HaUI.ImplSolution.dxos.farm.model.NotificationType;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import HaUI.ImplSolution.dxos.farm.model.RiceVariety;
import HaUI.ImplSolution.dxos.farm.model.SeasonType;
import HaUI.ImplSolution.dxos.farm.model.TreatmentApplication;
import HaUI.ImplSolution.dxos.farm.model.TreatmentConfirmationStatus;
import HaUI.ImplSolution.dxos.farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiagnosisRepository;
import HaUI.ImplSolution.dxos.farm.repository.DiseaseReportRepository;
import HaUI.ImplSolution.dxos.farm.repository.HarvestRescheduleRepository;
import HaUI.ImplSolution.dxos.farm.repository.ParcelRepository;
import HaUI.ImplSolution.dxos.farm.repository.RiceVarietyRepository;
import HaUI.ImplSolution.dxos.farm.repository.TreatmentApplicationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CropSeasonService {

  private static final Logger log = LoggerFactory.getLogger(CropSeasonService.class);
  // A harvest cannot be declared for a day further back than this, so old dates cannot be used to
  // slip past the isolation lock.
  private static final int MAX_HARVEST_BACKDATE_DAYS = 30;
  // A new expected harvest date may not be further ahead than this.
  private static final int MAX_RESCHEDULE_AHEAD_DAYS = 90;

  private final CropSeasonRepository cropSeasonRepository;
  private final ParcelRepository parcelRepository;
  private final TreatmentApplicationRepository treatmentApplicationRepository;
  private final RiceVarietyRepository riceVarietyRepository;
  private final DiagnosisRepository diagnosisRepository;
  private final DiseaseReportRepository diseaseReportRepository;
  private final HarvestRescheduleRepository harvestRescheduleRepository;
  private final NotificationService notificationService;
  private final CurrentUser currentUserService;
  private final WorkflowOperations workflowService;
  private final SeasonCalendar seasonCalendar;
  private final PendingRequestLimit pendingRequestLimiter;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public CropSeasonService(CropSeasonRepository cropSeasonRepository,
      ParcelRepository parcelRepository,
      TreatmentApplicationRepository treatmentApplicationRepository,
      RiceVarietyRepository riceVarietyRepository, DiagnosisRepository diagnosisRepository,
      DiseaseReportRepository diseaseReportRepository,
      HarvestRescheduleRepository harvestRescheduleRepository,
      NotificationService notificationService, CurrentUser currentUserService,
      WorkflowOperations workflowService, SeasonCalendar seasonCalendar,
      PendingRequestLimit pendingRequestLimiter, ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.cropSeasonRepository = cropSeasonRepository;
    this.parcelRepository = parcelRepository;
    this.treatmentApplicationRepository = treatmentApplicationRepository;
    this.riceVarietyRepository = riceVarietyRepository;
    this.diagnosisRepository = diagnosisRepository;
    this.diseaseReportRepository = diseaseReportRepository;
    this.harvestRescheduleRepository = harvestRescheduleRepository;
    this.notificationService = notificationService;
    this.currentUserService = currentUserService;
    this.workflowService = workflowService;
    this.seasonCalendar = seasonCalendar;
    this.pendingRequestLimiter = pendingRequestLimiter;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public List<CropSeasonResponse> listByParcelCode(String parcelCode, boolean includeDeleted) {
    if (includeDeleted && !currentUserService.isAdmin()) {
      throw new AccessDeniedException("includeDeleted is limited to ADMIN");
    }
    if (currentUserService.isFarmer()) {
      findAccessibleParcelOrThrow(parcelCode);
    }
    List<CropSeason> seasons =
        includeDeleted ? cropSeasonRepository.findByParcel_CodeOrderByIdAsc(parcelCode)
            : cropSeasonRepository.findByParcel_CodeAndDeletedAtIsNullOrderByIdAsc(parcelCode);
    return seasons.stream().map(CropSeasonService::toResponse).toList();
  }

  public CropSeasonResponse getById(Long id) {
    return toResponse(findAccessibleOrThrow(id));
  }

  // Reuses harvest()'s exact lookups so this answer and the poka-yoke check never disagree.
  public IsolationStatusResponse getIsolationStatus(Long id) {
    return isolationOf(findAccessibleOrThrow(id));
  }

  // Active seasons under isolation that the user can see, so a parcel list needs one request.
  public List<IsolationStatusResponse> listIsolated() {
    List<CropSeason> active;
    if (currentUserService.isFarmer()) {
      active = currentUserService.currentWorkspace()
          .map(household -> cropSeasonRepository
              .findByParcel_HouseholdIdAndStatusAndDeletedAtIsNullOrderByIdAsc(household.id(),
                  CropSeasonStatus.ACTIVE))
          .orElse(List.of());
    } else {
      active =
          cropSeasonRepository.findByStatusAndDeletedAtIsNullOrderByIdAsc(CropSeasonStatus.ACTIVE);
    }
    return active.stream().map(this::isolationOf).filter(IsolationStatusResponse::isolated)
        .toList();
  }

  private IsolationStatusResponse isolationOf(CropSeason season) {
    Long id = season.getId();
    var pending = treatmentApplicationRepository
        .findTopByCropSeasonIdAndConfirmationStatusOrderByHarvestLockedUntilDesc(id,
            TreatmentConfirmationStatus.PENDING_CONFIRMATION);
    if (pending.isPresent()) {
      return toPendingIsolationStatus(season, pending.get());
    }
    return treatmentApplicationRepository
        .findTopByCropSeasonIdAndConfirmationStatusOrderByHarvestLockedUntilDesc(id,
            TreatmentConfirmationStatus.CONFIRMED)
        .map(application -> toIsolationStatus(season, application))
        .orElseGet(() -> notIsolated(season, null, null));
  }

  // Season type and harvest date the farmer is shown before saving; the checks in create() use
  // the same figures.
  public CropSeasonSuggestionResponse suggest(String riceVarietyCode, LocalDate startDate) {
    RiceVariety variety = findVarietyOrThrow(riceVarietyCode);
    return new CropSeasonSuggestionResponse(
        seasonCalendar.suggest(startDate).map(SeasonType::name).orElse(null),
        suggestedHarvest(variety, startDate), startDate.plusDays(variety.getGrowthDaysMin()),
        startDate.plusDays(variety.getGrowthDaysMax()));
  }

  // A farmer's season waits for an administrator; one an administrator starts is approved at once.
  @Transactional
  public CropSeasonResponse create(String parcelCode, CropSeasonCreateRequest request,
      UUID clientRequestId) {
    Parcel parcel = findAccessibleParcelOrThrow(parcelCode);
    if (clientRequestId != null) {
      var existing = cropSeasonRepository.findByClientRequestId(clientRequestId);
      if (existing.isPresent()) {
        return toResponse(requireSameParcel(existing.get(), parcel));
      }
    }
    if (parcel.getApprovalStatus() != ApprovalStatus.APPROVED) {
      throw new PokaYokeViolationException("PARCEL_NOT_APPROVED",
          "Thửa %s chưa được duyệt nên chưa tạo được vụ mùa".formatted(parcelCode));
    }
    boolean farmer = currentUserService.isFarmer();
    if (farmer) {
      pendingRequestLimiter.requireCapacity();
    }
    CropSeason season = CropSeason.builder().parcel(parcel).status(CropSeasonStatus.ACTIVE)
        .approvalStatus(ApprovalStatus.PENDING).clientRequestId(clientRequestId).build();
    fill(season, request);
    CropSeason saved = save(season, parcelCode, clientRequestId != null);
    CaseView workflowCase = workflowService.createCase(ApprovalCaseTypes.CROP_SEASON_APPROVAL,
        ApprovalCaseTypes.CROP_SEASON_ENTITY, saved.getId(), currentActor());
    return toResponse(settle(saved, workflowCase, farmer, false));
  }

  // A rejected season goes back to the administrator with the corrected values.
  @Transactional
  public CropSeasonResponse resubmit(Long id, CropSeasonCreateRequest request) {
    CropSeason season = findAccessibleOrThrow(id);
    if (season.getApprovalStatus() != ApprovalStatus.REJECTED) {
      throw new InvalidStateException("Only a rejected crop season can be sent again: " + id);
    }
    boolean farmer = currentUserService.isFarmer();
    if (farmer) {
      pendingRequestLimiter.requireCapacity();
    }
    fill(season, request);
    season.setApprovalStatus(ApprovalStatus.PENDING);
    season.setRejectionReason(null);
    CropSeason saved = save(season, season.getParcel().getCode(), false);
    CaseView workflowCase = workflowService.recordTransition(ApprovalCaseTypes.CROP_SEASON_APPROVAL,
        ApprovalCaseTypes.CROP_SEASON_ENTITY, saved.getId(), WorkflowStatus.PENDING, currentActor(),
        null, null);
    return toResponse(settle(saved, workflowCase, farmer, true));
  }

  // Copies the request onto the season after checking variety, season type, harvest date and the
  // slots the parcel allows.
  private void fill(CropSeason season, CropSeasonCreateRequest request) {
    LocalDate startDate = request.startDate();
    RiceVariety variety = findVarietyOrThrow(request.riceVarietyCode());
    SeasonType seasonType = request.seasonType() != null ? request.seasonType()
        : seasonCalendar.suggest(startDate).orElseThrow(() -> new IllegalArgumentException(
            "seasonType is required: the start month matches no season window"));
    LocalDate expected = request.expectedHarvestDate();
    String override =
        request.reasonForOverride() == null || request.reasonForOverride().isBlank() ? null
            : request.reasonForOverride().strip();
    if (expected == null) {
      expected = suggestedHarvest(variety, startDate);
    } else if (expected.isBefore(startDate)) {
      throw new IllegalArgumentException("expectedHarvestDate must not be before startDate");
    } else if ((expected.isBefore(startDate.plusDays(variety.getGrowthDaysMin()))
        || expected.isAfter(startDate.plusDays(variety.getGrowthDaysMax()))) && override == null) {
      throw new IllegalArgumentException(
          "reasonForOverride is required: expectedHarvestDate is outside %d-%d days of growth for %s"
              .formatted(variety.getGrowthDaysMin(), variety.getGrowthDaysMax(),
                  variety.getName()));
    }
    Parcel parcel = season.getParcel();
    Long self = season.getId() == null ? -1L : season.getId();
    if (cropSeasonRepository
        .existsByParcel_IdAndStatusAndDeletedAtIsNullAndApprovalStatusNotAndIdNot(parcel.getId(),
            CropSeasonStatus.ACTIVE, ApprovalStatus.REJECTED, self)) {
      throw new InvalidStateException(
          "Parcel already has an active crop season: " + parcel.getCode());
    }
    int year = startDate.getYear();
    if (cropSeasonRepository
        .existsByParcel_IdAndSeasonYearAndSeasonTypeAndDeletedAtIsNullAndApprovalStatusNotAndIdNot(
            parcel.getId(), year, seasonType, ApprovalStatus.REJECTED, self)) {
      throw new InvalidStateException(
          "Parcel %s already has a %s season in %d".formatted(parcel.getCode(), seasonType, year));
    }
    season.setRiceVariety(variety.getName());
    season.setRiceVarietyCode(variety.getCode());
    season.setStartDate(startDate);
    season.setExpectedHarvestDate(expected);
    season.setReasonForOverride(override);
    season.setSeasonType(seasonType);
    season.setSeasonYear(year);
    season.setCode("%s-%d%s".formatted(parcel.getCode(), year, seasonType.codeSuffix()));
  }

  private CropSeason save(CropSeason season, String parcelCode, boolean hasIdempotencyKey) {
    try {
      return cropSeasonRepository.saveAndFlush(season);
    } catch (DataIntegrityViolationException e) {
      // Lost a race against a concurrent create for the same slot or the same Idempotency-Key.
      throw new InvalidStateException(
          hasIdempotencyKey ? "Idempotency-Key is already being processed"
              : "Parcel already has a crop season in this slot: " + parcelCode);
    }
  }

  private CropSeason settle(CropSeason season, CaseView workflowCase, boolean farmer,
      boolean resubmitted) {
    if (!farmer) {
      workflowService.decide(workflowCase.id(), WorkflowStatus.APPROVED, currentActor(), null);
      return season;
    }
    eventPublisher.publishEvent(new CropSeasonCreatedEvent(season.getCode(),
        season.getParcel().getCode(), season.getParcel().getFarmerName(), season.getRiceVariety(),
        season.getStartDate(), season.getExpectedHarvestDate(), resubmitted));
    return season;
  }

  private WorkflowActor currentActor() {
    return new WorkflowActor(currentUserService.currentUserId(),
        currentUserService.currentFullName());
  }

  private RiceVariety findVarietyOrThrow(String code) {
    return riceVarietyRepository.findByCodeAndActiveTrue(code)
        .orElseThrow(() -> new IllegalArgumentException("Unknown rice variety: " + code));
  }

  private static LocalDate suggestedHarvest(RiceVariety variety, LocalDate startDate) {
    return startDate.plusDays((variety.getGrowthDaysMin() + variety.getGrowthDaysMax()) / 2);
  }

  private static CropSeason requireSameParcel(CropSeason season, Parcel parcel) {
    if (!season.getParcel().getId().equals(parcel.getId())) {
      throw new InvalidStateException("Idempotency-Key was used for a different request");
    }
    return season;
  }

  // What the farmer sees before declaring a harvest: how early it is, the isolation state and the
  // open work that does not block it.
  public HarvestReadinessResponse harvestReadiness(Long id) {
    CropSeason season = findAccessibleOrThrow(id);
    LocalDate today = LocalDate.now(clock);
    LocalDate expected = season.getExpectedHarvestDate();
    long daysBefore =
        expected != null && expected.isAfter(today) ? ChronoUnit.DAYS.between(today, expected) : 0;
    return new HarvestReadinessResponse(id, expected, daysBefore, daysBefore > 0,
        isolationOf(season), harvestWarnings(id));
  }

  private List<HarvestWarning> harvestWarnings(Long seasonId) {
    List<HarvestWarning> warnings = new ArrayList<>();
    long awaiting = diseaseReportRepository.countAwaitingDecision(seasonId);
    if (awaiting > 0) {
      warnings.add(new HarvestWarning("PENDING_REPORTS",
          "Còn %d báo cáo bệnh chưa có kết quả duyệt của kỹ sư.".formatted(awaiting)));
    }
    long untreated = diagnosisRepository.countUntreatedApproved(seasonId);
    if (untreated > 0) {
      warnings.add(new HarvestWarning("UNTREATED_RECOMMENDATIONS",
          "Còn %d khuyến nghị đã được duyệt nhưng chưa ghi nhận phun thuốc.".formatted(untreated)));
    }
    return warnings;
  }

  @Transactional
  public CropSeasonResponse harvest(Long id, HarvestRequest request) {
    CropSeason season = findAccessibleOrThrow(id);
    if (season.getApprovalStatus() != ApprovalStatus.APPROVED) {
      throw new PokaYokeViolationException("CROP_SEASON_NOT_APPROVED",
          "Crop season is not approved yet: " + id);
    }
    if (season.getStatus() == CropSeasonStatus.HARVESTED) {
      throw new InvalidStateException("Crop season already harvested: " + id);
    }
    LocalDate today = LocalDate.now(clock);
    LocalDate actual = request.actualHarvestDate() != null ? request.actualHarvestDate() : today;
    requireHarvestDate(season, actual, today);
    BigDecimal parcelArea = season.getParcel().getAreaHectares();
    BigDecimal area =
        request.harvestedAreaHectares() != null ? request.harvestedAreaHectares() : parcelArea;
    if (area.compareTo(parcelArea) > 0) {
      throw new IllegalArgumentException(
          "harvestedAreaHectares must not exceed the parcel area of %s ha".formatted(parcelArea));
    }
    boolean onBehalf = !currentUserService.isFarmer();
    String onBehalfReason = blankToNull(request.reasonOnBehalf());
    if (onBehalf && onBehalfReason == null) {
      throw new IllegalArgumentException(
          "reasonOnBehalf is required when an administrator declares the harvest for a farmer");
    }
    String earlyReason = requireEarlyConfirmation(season, actual, request);
    requireNoIsolation(season, actual);

    season.setStatus(CropSeasonStatus.HARVESTED);
    season.setHarvestedAt(Instant.now(clock));
    season.setActualHarvestDate(actual);
    season.setYieldKg(request.yieldKg());
    season.setHarvestedAreaHectares(area);
    season.setHarvestedByUserId(currentUserService.currentUserId());
    season.setHarvestedByName(currentUserService.currentFullName());
    season.setEarlyReason(earlyReason);
    season.setHarvestOnBehalfReason(onBehalf ? onBehalfReason : null);
    CropSeason saved = cropSeasonRepository.save(season);
    if (onBehalf) {
      log.info("Harvest of season {} declared by {} on behalf of household {}: {}", saved.getId(),
          saved.getHarvestedByUserId(), saved.getParcel().getHouseholdId(), onBehalfReason);
      notificationService.notifyHousehold(saved.getParcel().getHouseholdId(),
          NotificationType.HARVEST_RECORDED, "Vụ mùa đã được ghi nhận thu hoạch",
          "Quản trị viên đã ghi nhận thu hoạch vụ %s thay bạn (%s kg, %s ha). Lý do: %s.".formatted(
              seasonLabel(saved), saved.getYieldKg().stripTrailingZeros().toPlainString(),
              saved.getHarvestedAreaHectares().stripTrailingZeros().toPlainString(),
              onBehalfReason),
          "CROP_SEASON", String.valueOf(saved.getId()));
    }
    return toResponse(saved);
  }

  private void requireHarvestDate(CropSeason season, LocalDate actual, LocalDate today) {
    if (actual.isAfter(today)) {
      throw new IllegalArgumentException("actualHarvestDate must not be in the future");
    }
    if (actual.isBefore(season.getStartDate())) {
      throw new IllegalArgumentException("actualHarvestDate must not be before the season start");
    }
    if (actual.isBefore(today.minusDays(MAX_HARVEST_BACKDATE_DAYS))) {
      throw new IllegalArgumentException(
          "actualHarvestDate must not be more than %d days in the past"
              .formatted(MAX_HARVEST_BACKDATE_DAYS));
    }
  }

  // Returns the reason to keep, or null when the harvest is not early.
  private static String requireEarlyConfirmation(CropSeason season, LocalDate actual,
      HarvestRequest request) {
    LocalDate expected = season.getExpectedHarvestDate();
    if (expected == null || !actual.isBefore(expected)) {
      return null;
    }
    String reason = blankToNull(request.earlyReason());
    if (!Boolean.TRUE.equals(request.confirmEarly()) || reason == null) {
      throw new PokaYokeViolationException("HARVEST_TOO_EARLY",
          "Harvest is before the expected date %s: send confirmEarly=true with earlyReason"
              .formatted(expected));
    }
    return reason;
  }

  // A report still waiting for the engineer blocks the harvest as much as a confirmed isolation.
  private void requireNoIsolation(CropSeason season, LocalDate harvestDate) {
    Long id = season.getId();
    if (treatmentApplicationRepository.existsByCropSeasonIdAndConfirmationStatus(id,
        TreatmentConfirmationStatus.PENDING_CONFIRMATION)) {
      throw new PokaYokeViolationException("TREATMENT_PENDING_CONFIRMATION",
          "A spraying report is waiting for an engineer; harvest is blocked until it is decided");
    }
    treatmentApplicationRepository
        .findTopByCropSeasonIdAndConfirmationStatusOrderByHarvestLockedUntilDesc(id,
            TreatmentConfirmationStatus.CONFIRMED)
        .filter(application -> application.getHarvestLockedUntil().isAfter(harvestDate))
        .ifPresent(application -> {
          throw pokaYokeViolation(application);
        });
  }

  // The farmer moves the expected date; every change is kept in the history.
  @Transactional
  public CropSeasonResponse rescheduleHarvest(Long id, HarvestRescheduleRequest request) {
    CropSeason season = findAccessibleOrThrow(id);
    if (season.getApprovalStatus() != ApprovalStatus.APPROVED) {
      throw new PokaYokeViolationException("CROP_SEASON_NOT_APPROVED",
          "Crop season is not approved yet: " + id);
    }
    if (season.getStatus() != CropSeasonStatus.ACTIVE) {
      throw new InvalidStateException("Only an active crop season can be rescheduled: " + id);
    }
    LocalDate today = LocalDate.now(clock);
    LocalDate newDate = request.newExpectedHarvestDate();
    LocalDate previous = season.getExpectedHarvestDate();
    if (newDate.isBefore(today)) {
      throw new IllegalArgumentException("newExpectedHarvestDate must not be in the past");
    }
    if (newDate.isAfter(today.plusDays(MAX_RESCHEDULE_AHEAD_DAYS))) {
      throw new IllegalArgumentException("newExpectedHarvestDate must be within %d days from today"
          .formatted(MAX_RESCHEDULE_AHEAD_DAYS));
    }
    if (newDate.equals(previous)) {
      throw new IllegalArgumentException("newExpectedHarvestDate is the current date");
    }
    harvestRescheduleRepository.save(HarvestReschedule.builder().cropSeason(season)
        .previousDate(previous != null ? previous : season.getStartDate()).newDate(newDate)
        .reason(request.reason().strip()).rescheduledByUserId(currentUserService.currentUserId())
        .rescheduledByName(currentUserService.currentFullName()).build());
    season.setExpectedHarvestDate(newDate);
    return toResponse(cropSeasonRepository.save(season));
  }

  public List<HarvestRescheduleResponse> listReschedules(Long id) {
    findAccessibleOrThrow(id);
    return harvestRescheduleRepository.findByCropSeasonIdOrderByIdAsc(id).stream()
        .map(item -> new HarvestRescheduleResponse(item.getId(), item.getPreviousDate(),
            item.getNewDate(), item.getReason(), item.getRescheduledByName(),
            item.getRescheduledAt()))
        .toList();
  }

  private static String seasonLabel(CropSeason season) {
    return season.getCode() != null ? season.getCode() : "#" + season.getId();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  @Transactional
  public void delete(Long id) {
    CropSeason season = findAccessibleOrThrow(id);
    season.setDeletedAt(Instant.now(clock));
    cropSeasonRepository.save(season);
  }

  private Parcel findAccessibleParcelOrThrow(String parcelCode) {
    return parcelRepository.findByCodeAndDeletedAtIsNull(parcelCode)
        .filter(currentUserService::canAccess)
        .orElseThrow(() -> new ResourceNotFoundException("Parcel not found: " + parcelCode));
  }

  // A season is reachable only while neither it nor its parcel is soft-deleted.
  private CropSeason findAccessibleOrThrow(Long id) {
    return cropSeasonRepository.findById(id).filter(season -> season.getDeletedAt() == null)
        .filter(season -> season.getParcel().getDeletedAt() == null)
        .filter(season -> currentUserService.canAccess(season.getParcel()))
        .orElseThrow(() -> new ResourceNotFoundException("Crop season not found: " + id));
  }

  private static PokaYokeViolationException pokaYokeViolation(TreatmentApplication application) {
    return new PokaYokeViolationException("POKA_YOKE_VIETGAP_VIOLATION",
        "Crop season is still under pesticide isolation until "
            + application.getHarvestLockedUntil(),
        Map.of("harvestLockedUntil", application.getHarvestLockedUntil()));
  }

  private IsolationStatusResponse toIsolationStatus(CropSeason season,
      TreatmentApplication application) {
    LocalDate lockedUntil = application.getHarvestLockedUntil();
    String pesticideCode = application.getPesticide().getCode();
    LocalDate today = LocalDate.now(clock);
    if (!lockedUntil.isAfter(today)) {
      return notIsolated(season, lockedUntil, pesticideCode);
    }

    long daysRemaining = ChronoUnit.DAYS.between(today, lockedUntil);
    String message =
        "Thửa %s còn cách ly do vừa phun %s, còn %d ngày nữa (đến hết ngày %s) mới được thu hoạch."
            .formatted(season.getParcel().getCode(), application.getPesticide().getName(),
                daysRemaining, lockedUntil);
    return new IsolationStatusResponse(season.getId(), season.getParcel().getCode(), true,
        lockedUntil, daysRemaining, pesticideCode, message);
  }

  private IsolationStatusResponse toPendingIsolationStatus(CropSeason season,
      TreatmentApplication application) {
    long daysRemaining = Math.max(0,
        ChronoUnit.DAYS.between(LocalDate.now(clock), application.getHarvestLockedUntil()));
    String message =
        "Thửa %s đã khai phun %s ngày %s, đang chờ kỹ sư xác nhận nên chưa thu hoạch được."
            .formatted(season.getParcel().getCode(), application.getPesticide().getName(),
                application.getAppliedAt());
    return new IsolationStatusResponse(season.getId(), season.getParcel().getCode(), true,
        application.getHarvestLockedUntil(), daysRemaining, application.getPesticide().getCode(),
        message);
  }

  private static IsolationStatusResponse notIsolated(CropSeason season, LocalDate lastLockedUntil,
      String pesticideCode) {
    String message = lastLockedUntil == null
        ? "Thửa %s chưa ghi nhận lần phun thuốc nào, không bị cách ly."
            .formatted(season.getParcel().getCode())
        : "Thửa %s đã hết cách ly (hết hạn ngày %s), có thể thu hoạch."
            .formatted(season.getParcel().getCode(), lastLockedUntil);
    return new IsolationStatusResponse(season.getId(), season.getParcel().getCode(), false,
        lastLockedUntil, null, pesticideCode, message);
  }

  public static CropSeasonResponse toResponse(CropSeason season) {
    return new CropSeasonResponse(season.getId(), season.getCode(), season.getParcel().getCode(),
        season.getRiceVariety(), season.getRiceVarietyCode(),
        season.getSeasonType() != null ? season.getSeasonType().name() : null,
        season.getStartDate(), season.getExpectedHarvestDate(), season.getReasonForOverride(),
        season.getStatus().name(), season.getApprovalStatus().name(), season.getRejectionReason(),
        season.getHarvestedAt(), season.getActualHarvestDate(), season.getYieldKg(),
        season.getHarvestedAreaHectares(), season.getHarvestedByName(), season.getEarlyReason(),
        season.getHarvestOnBehalfReason());
  }
}
