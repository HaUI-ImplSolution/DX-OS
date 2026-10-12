// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.DeviceRequest;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.DeviceResponse;
import HaUI.ImplSolution.dxos.lab.model.Device;
import HaUI.ImplSolution.dxos.lab.model.DeviceStatus;
import HaUI.ImplSolution.dxos.lab.model.Room;
import HaUI.ImplSolution.dxos.lab.repository.DeviceRepository;
import HaUI.ImplSolution.dxos.lab.repository.RoomRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The device inventory. Loans and maintenance move a device between statuses; retiring is done
// here.
@Service
@Transactional(readOnly = true)
public class DeviceService {

  private final DeviceRepository deviceRepository;
  private final RoomRepository roomRepository;

  public DeviceService(DeviceRepository deviceRepository, RoomRepository roomRepository) {
    this.deviceRepository = deviceRepository;
    this.roomRepository = roomRepository;
  }

  public List<DeviceResponse> list(Long roomId, DeviceStatus status, String deviceType) {
    Specification<Device> filter = Specification.unrestricted();
    if (roomId != null) {
      filter = filter.and((root, query, cb) -> cb.equal(root.get("room").get("id"), roomId));
    }
    if (status != null) {
      filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (deviceType != null && !deviceType.isBlank()) {
      filter =
          filter.and((root, query, cb) -> cb.equal(root.get("deviceType"), deviceType.strip()));
    }
    return deviceRepository.findAll(filter, Sort.by("assetCode")).stream().map(DeviceResponse::of)
        .toList();
  }

  public DeviceResponse get(Long id) {
    return DeviceResponse.of(findOrThrow(id));
  }

  @Transactional
  public DeviceResponse create(DeviceRequest request) {
    String assetCode = request.assetCode().strip();
    if (deviceRepository.existsByAssetCode(assetCode)) {
      throw new InvalidStateException("Asset code already exists: " + assetCode);
    }
    Device device = Device.builder().assetCode(assetCode).build();
    fill(device, request);
    return DeviceResponse.of(deviceRepository.save(device));
  }

  @Transactional
  public DeviceResponse update(Long id, DeviceRequest request) {
    Device device = findOrThrow(id);
    String assetCode = request.assetCode().strip();
    if (!device.getAssetCode().equals(assetCode) && deviceRepository.existsByAssetCode(assetCode)) {
      throw new InvalidStateException("Asset code already exists: " + assetCode);
    }
    device.setAssetCode(assetCode);
    fill(device, request);
    return DeviceResponse.of(deviceRepository.save(device));
  }

  // A device that is out on loan comes back first; retired devices stay for the history.
  @Transactional
  public DeviceResponse retire(Long id) {
    Device device = deviceRepository.findByIdForUpdate(id)
        .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + id));
    if (device.getStatus() == DeviceStatus.ON_LOAN) {
      throw new PokaYokeViolationException("DEVICE_ON_LOAN",
          "Thiết bị %s đang được mượn nên chưa thể thanh lý".formatted(device.getAssetCode()));
    }
    device.setStatus(DeviceStatus.RETIRED);
    return DeviceResponse.of(deviceRepository.save(device));
  }

  Device findOrThrow(Long id) {
    return deviceRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + id));
  }

  private void fill(Device device, DeviceRequest request) {
    device.setDeviceType(request.deviceType().strip());
    device.setModel(blankToNull(request.model()));
    device.setSerialNumber(blankToNull(request.serialNumber()));
    device.setPurchasedOn(request.purchasedOn());
    device.setWarrantyUntil(request.warrantyUntil());
    Room room = null;
    if (request.roomId() != null) {
      room = roomRepository.findById(request.roomId())
          .orElseThrow(() -> new IllegalArgumentException("Unknown room: " + request.roomId()));
    }
    device.setRoom(room);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
