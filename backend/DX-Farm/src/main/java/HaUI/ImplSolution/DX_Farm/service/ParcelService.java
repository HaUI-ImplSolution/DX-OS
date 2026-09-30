package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.ParcelCreateRequest;
import HaUI.ImplSolution.DX_Farm.dto.ParcelResponse;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.Parcel;
import HaUI.ImplSolution.DX_Farm.repository.ParcelRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ParcelService {

  private final ParcelRepository parcelRepository;
  private final CurrentUserService currentUserService;

  public ParcelService(ParcelRepository parcelRepository, CurrentUserService currentUserService) {
    this.parcelRepository = parcelRepository;
    this.currentUserService = currentUserService;
  }

  public List<ParcelResponse> listAll() {
    List<Parcel> parcels = currentUserService.isFarmer()
        ? parcelRepository.findByFarmerNameIgnoreCase(currentUserService.currentFullName())
        : parcelRepository.findAll();
    return parcels.stream().map(ParcelService::toResponse).toList();
  }

  public ParcelResponse getByCode(String code) {
    return toResponse(findParcelOrThrow(code));
  }

  @Transactional
  public ParcelResponse create(ParcelCreateRequest request) {
    String farmerName =
        currentUserService.isFarmer() ? currentUserService.currentFullName() : request.farmerName();
    Parcel parcel = Parcel.builder().code(request.code()).farmerName(farmerName)
        .areaHectares(request.areaHectares()).location(request.location())
        .latitude(request.latitude()).longitude(request.longitude()).build();
    return toResponse(parcelRepository.save(parcel));
  }

  private Parcel findParcelOrThrow(String code) {
    return parcelRepository.findByCode(code).filter(currentUserService::canAccess)
        .orElseThrow(() -> new ResourceNotFoundException("Parcel not found: " + code));
  }

  private static ParcelResponse toResponse(Parcel parcel) {
    return new ParcelResponse(parcel.getId(), parcel.getCode(), parcel.getFarmerName(),
        parcel.getAreaHectares(), parcel.getLocation(), parcel.getLatitude(), parcel.getLongitude(),
        parcel.getCreatedAt(), parcel.getUpdatedAt());
  }
}
