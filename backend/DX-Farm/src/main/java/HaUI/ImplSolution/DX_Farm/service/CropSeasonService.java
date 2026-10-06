package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.CropSeasonCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.CropSeasonResponse;
import HaUI.ImplSolution.DX_Farm.dto.IsolationStatusResponse;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.PokaYokeViolationException;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.CropSeasonStatus;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.model.TreatmentApplication;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.repository.ParcelRepository;
import HaUI.ImplSolution.DX_Farm.repository.TreatmentApplicationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CropSeasonService {

  private final CropSeasonRepository cropSeasonRepository;
  private final ParcelRepository parcelRepository;
  private final TreatmentApplicationRepository treatmentApplicationRepository;
  private final CurrentUserService currentUserService;
  private final Clock clock;

  public CropSeasonService(CropSeasonRepository cropSeasonRepository,
      ParcelRepository parcelRepository,
      TreatmentApplicationRepository treatmentApplicationRepository,
      CurrentUserService currentUserService, Clock clock) {
    this.cropSeasonRepository = cropSeasonRepository;
    this.parcelRepository = parcelRepository;
    this.treatmentApplicationRepository = treatmentApplicationRepository;
    this.currentUserService = currentUserService;
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

  // Reuses harvest()'s exact lookup so this answer and the poka-yoke check never disagree.
  public IsolationStatusResponse getIsolationStatus(Long id) {
    CropSeason season = findAccessibleOrThrow(id);
    return treatmentApplicationRepository.findTopByCropSeasonIdOrderByHarvestLockedUntilDesc(id)
        .map(application -> toIsolationStatus(season, application))
        .orElseGet(() -> notIsolated(season, null, null));
  }

  @Transactional
  public CropSeasonResponse create(String parcelCode, CropSeasonCreateRequest request) {
    Parcel parcel = findAccessibleParcelOrThrow(parcelCode);
    if (request.expectedHarvestDate() != null
        && request.expectedHarvestDate().isBefore(request.startDate())) {
      throw new IllegalArgumentException("expectedHarvestDate must not be before startDate");
    }
    if (cropSeasonRepository.existsByParcel_IdAndStatusAndDeletedAtIsNull(parcel.getId(),
        CropSeasonStatus.ACTIVE)) {
      throw new InvalidStateException("Parcel already has an active crop season: " + parcelCode);
    }
    CropSeason season = CropSeason.builder().parcel(parcel).riceVariety(request.riceVariety())
        .startDate(request.startDate()).expectedHarvestDate(request.expectedHarvestDate())
        .status(CropSeasonStatus.ACTIVE).build();
    try {
      return toResponse(cropSeasonRepository.saveAndFlush(season));
    } catch (DataIntegrityViolationException e) {
      // Lost a race against a concurrent create: the partial unique index allows one ACTIVE season.
      throw new InvalidStateException("Parcel already has an active crop season: " + parcelCode);
    }
  }

  @Transactional
  public CropSeasonResponse harvest(Long id) {
    CropSeason season = findAccessibleOrThrow(id);
    if (season.getStatus() == CropSeasonStatus.HARVESTED) {
      throw new InvalidStateException("Crop season already harvested: " + id);
    }

    treatmentApplicationRepository.findTopByCropSeasonIdOrderByHarvestLockedUntilDesc(id)
        .filter(application -> application.getHarvestLockedUntil().isAfter(LocalDate.now(clock)))
        .ifPresent(application -> {
          throw pokaYokeViolation(application);
        });

    season.setStatus(CropSeasonStatus.HARVESTED);
    season.setHarvestedAt(Instant.now(clock));
    return toResponse(cropSeasonRepository.save(season));
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
        application.getHarvestLockedUntil());
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
    return new CropSeasonResponse(season.getId(), season.getParcel().getCode(),
        season.getRiceVariety(), season.getStartDate(), season.getExpectedHarvestDate(),
        season.getStatus().name(), season.getHarvestedAt());
  }
}
