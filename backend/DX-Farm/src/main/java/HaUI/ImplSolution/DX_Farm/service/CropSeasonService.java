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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CropSeasonService {

  private final CropSeasonRepository cropSeasonRepository;
  private final ParcelRepository parcelRepository;
  private final TreatmentApplicationRepository treatmentApplicationRepository;

  public CropSeasonService(CropSeasonRepository cropSeasonRepository,
      ParcelRepository parcelRepository,
      TreatmentApplicationRepository treatmentApplicationRepository) {
    this.cropSeasonRepository = cropSeasonRepository;
    this.parcelRepository = parcelRepository;
    this.treatmentApplicationRepository = treatmentApplicationRepository;
  }

  public List<CropSeasonResponse> listByParcelCode(String parcelCode) {
    return cropSeasonRepository.findByParcel_Code(parcelCode).stream()
        .map(CropSeasonService::toResponse).toList();
  }

  public CropSeasonResponse getById(Long id) {
    return toResponse(findOrThrow(id));
  }

  // Reuses harvest()'s exact lookup so this answer and the poka-yoke check never disagree.
  public IsolationStatusResponse getIsolationStatus(Long id) {
    CropSeason season = findOrThrow(id);
    return treatmentApplicationRepository.findTopByCropSeasonIdOrderByHarvestLockedUntilDesc(id)
        .map(application -> toIsolationStatus(season, application))
        .orElseGet(() -> notIsolated(season, null, null));
  }

  @Transactional
  public CropSeasonResponse create(String parcelCode, CropSeasonCreateRequest request) {
    Parcel parcel = parcelRepository.findByCode(parcelCode)
        .orElseThrow(() -> new ResourceNotFoundException("Parcel not found: " + parcelCode));
    CropSeason season = CropSeason.builder().parcel(parcel).riceVariety(request.riceVariety())
        .startDate(request.startDate()).expectedHarvestDate(request.expectedHarvestDate())
        .status(CropSeasonStatus.ACTIVE).build();
    return toResponse(cropSeasonRepository.save(season));
  }

  @Transactional
  public CropSeasonResponse harvest(Long id) {
    CropSeason season = findOrThrow(id);
    if (season.getStatus() == CropSeasonStatus.HARVESTED) {
      throw new InvalidStateException("Crop season already harvested: " + id);
    }

    treatmentApplicationRepository.findTopByCropSeasonIdOrderByHarvestLockedUntilDesc(id)
        .filter(application -> application.getHarvestLockedUntil().isAfter(LocalDate.now()))
        .ifPresent(application -> {
          throw pokaYokeViolation(application);
        });

    season.setStatus(CropSeasonStatus.HARVESTED);
    season.setHarvestedAt(Instant.now());
    return toResponse(cropSeasonRepository.save(season));
  }

  private CropSeason findOrThrow(Long id) {
    return cropSeasonRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Crop season not found: " + id));
  }

  private static PokaYokeViolationException pokaYokeViolation(TreatmentApplication application) {
    return new PokaYokeViolationException("POKA_YOKE_VIETGAP_VIOLATION",
        "Crop season is still under pesticide isolation until "
            + application.getHarvestLockedUntil(),
        application.getHarvestLockedUntil());
  }

  private static IsolationStatusResponse toIsolationStatus(CropSeason season,
      TreatmentApplication application) {
    LocalDate lockedUntil = application.getHarvestLockedUntil();
    String pesticideCode = application.getPesticide().getCode();
    if (!lockedUntil.isAfter(LocalDate.now())) {
      return notIsolated(season, lockedUntil, pesticideCode);
    }

    long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), lockedUntil);
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
