// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.MaintenanceCompleteRequest;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.MaintenanceResponse;
import HaUI.ImplSolution.dxos.lab.dto.DeviceDtos.MaintenanceStartRequest;
import HaUI.ImplSolution.dxos.lab.model.Device;
import HaUI.ImplSolution.dxos.lab.model.DeviceMaintenance;
import HaUI.ImplSolution.dxos.lab.model.DeviceStatus;
import HaUI.ImplSolution.dxos.lab.model.MaintenanceKind;
import HaUI.ImplSolution.dxos.lab.model.Ticket;
import HaUI.ImplSolution.dxos.lab.repository.DeviceMaintenanceRepository;
import HaUI.ImplSolution.dxos.lab.repository.DeviceRepository;
import HaUI.ImplSolution.dxos.lab.repository.TicketRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The care history of a device. A repair or replacement keeps the device out of circulation until
// it is completed.
@Service
@Transactional(readOnly = true)
public class DeviceMaintenanceService {

  private final DeviceMaintenanceRepository maintenanceRepository;
  private final DeviceRepository deviceRepository;
  private final TicketRepository ticketRepository;
  private final LabUser user;
  private final Clock clock;

  public DeviceMaintenanceService(DeviceMaintenanceRepository maintenanceRepository,
      DeviceRepository deviceRepository, TicketRepository ticketRepository, LabUser user,
      Clock clock) {
    this.maintenanceRepository = maintenanceRepository;
    this.deviceRepository = deviceRepository;
    this.ticketRepository = ticketRepository;
    this.user = user;
    this.clock = clock;
  }

  public List<MaintenanceResponse> list(Long deviceId) {
    requireDevice(deviceId);
    return maintenanceRepository.findByDevice_IdOrderByStartedAtDescIdDesc(deviceId).stream()
        .map(MaintenanceResponse::of).toList();
  }

  @Transactional
  public MaintenanceResponse start(Long deviceId, MaintenanceStartRequest request) {
    Device device = deviceRepository.findByIdForUpdate(deviceId)
        .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
    switch (device.getStatus()) {
      case RETIRED -> throw new PokaYokeViolationException("DEVICE_RETIRED",
          "Thiết bị %s đã thanh lý".formatted(device.getAssetCode()));
      case ON_LOAN -> throw new PokaYokeViolationException("DEVICE_ON_LOAN",
          "Thiết bị %s đang được mượn, hãy nhận lại trước khi bảo trì"
              .formatted(device.getAssetCode()));
      default -> {
      }
    }
    if (maintenanceRepository.findByDevice_IdAndCompletedAtIsNull(deviceId).isPresent()) {
      throw new PokaYokeViolationException("MAINTENANCE_IN_PROGRESS",
          "Thiết bị %s đang có một đợt bảo trì chưa hoàn tất".formatted(device.getAssetCode()));
    }
    Ticket ticket = null;
    if (request.ticketId() != null) {
      ticket = ticketRepository.findById(request.ticketId())
          .orElseThrow(() -> new IllegalArgumentException("Unknown ticket: " + request.ticketId()));
    }
    DeviceMaintenance entry = maintenanceRepository.save(DeviceMaintenance.builder().device(device)
        .kind(request.kind()).note(request.note().strip()).ticket(ticket).performedById(user.id())
        .performedByName(user.name()).startedAt(clock.instant()).build());
    if (request.kind() != MaintenanceKind.INSPECTION) {
      device.setStatus(DeviceStatus.UNDER_REPAIR);
      deviceRepository.save(device);
    }
    return MaintenanceResponse.of(entry);
  }

  @Transactional
  public MaintenanceResponse complete(Long deviceId, Long maintenanceId,
      MaintenanceCompleteRequest request) {
    Device device = deviceRepository.findByIdForUpdate(deviceId)
        .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
    DeviceMaintenance entry =
        maintenanceRepository.findByIdAndDevice_Id(maintenanceId, deviceId).orElseThrow(
            () -> new ResourceNotFoundException("Maintenance entry not found: " + maintenanceId));
    if (entry.getCompletedAt() != null) {
      throw new InvalidStateException("Maintenance entry is already completed: " + maintenanceId);
    }
    entry.setCompletedAt(clock.instant());
    String note = request == null ? null : request.note();
    entry.setCompletionNote(note == null || note.isBlank() ? null : note.strip());
    maintenanceRepository.save(entry);
    if (device.getStatus() == DeviceStatus.UNDER_REPAIR) {
      device.setStatus(DeviceStatus.AVAILABLE);
      deviceRepository.save(device);
    }
    return MaintenanceResponse.of(entry);
  }

  // A device is written off by retiring it; opening a repair log for a damaged return goes through
  // here so the entry and the device status stay together.
  @Transactional
  public DeviceMaintenance openRepairForDamagedReturn(Device device, String note) {
    return maintenanceRepository.save(DeviceMaintenance.builder().device(device)
        .kind(MaintenanceKind.REPAIR).note(note).performedById(user.id())
        .performedByName(user.name()).startedAt(clock.instant()).build());
  }

  private void requireDevice(Long deviceId) {
    if (!deviceRepository.existsById(deviceId)) {
      throw new ResourceNotFoundException("Device not found: " + deviceId);
    }
  }
}
