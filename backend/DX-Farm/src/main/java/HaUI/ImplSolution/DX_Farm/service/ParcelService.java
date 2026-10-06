package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.ParcelCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.ParcelResponse;
import HaUI.ImplSolution.DX_Farm.exception.AccountNotLinkedException;
import HaUI.ImplSolution.DX_Farm.exception.InvalidStateException;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.core.identity.Household;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.repository.CropSeasonRepository;
import HaUI.ImplSolution.DX_Farm.core.identity.HouseholdRepository;
import HaUI.ImplSolution.DX_Farm.repository.ParcelRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
  private final CurrentUserService currentUserService;
  private final Clock clock;

  public ParcelService(ParcelRepository parcelRepository, HouseholdRepository householdRepository,
      CropSeasonRepository cropSeasonRepository, CurrentUserService currentUserService,
      Clock clock) {
    this.parcelRepository = parcelRepository;
    this.householdRepository = householdRepository;
    this.cropSeasonRepository = cropSeasonRepository;
    this.currentUserService = currentUserService;
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

  @Transactional
  public ParcelResponse create(ParcelCreateRequest request) {
    Household household = resolveOwner(request.householdCode());
    if (parcelRepository.existsByCodeAndDeletedAtIsNull(request.code())) {
      throw new InvalidStateException("Parcel code already exists: " + request.code());
    }
    Parcel parcel = Parcel.builder().code(request.code()).household(household)
        .farmerName(household.getFullName()).areaHectares(request.areaHectares())
        .location(request.location()).latitude(request.latitude()).longitude(request.longitude())
        .build();
    try {
      return toResponse(parcelRepository.saveAndFlush(parcel));
    } catch (DataIntegrityViolationException e) {
      throw new InvalidStateException("Parcel code already exists: " + request.code());
    }
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

  private Parcel findParcelOrThrow(String code) {
    return parcelRepository.findByCodeAndDeletedAtIsNull(code).filter(currentUserService::canAccess)
        .orElseThrow(() -> new ResourceNotFoundException("Parcel not found: " + code));
  }

  private static ParcelResponse toResponse(Parcel parcel) {
    return new ParcelResponse(parcel.getId(), parcel.getCode(), parcel.getHousehold().getCode(),
        parcel.getFarmerName(), parcel.getAreaHectares(), parcel.getLocation(),
        parcel.getLatitude(), parcel.getLongitude(), parcel.getCreatedAt(), parcel.getUpdatedAt());
  }
}
