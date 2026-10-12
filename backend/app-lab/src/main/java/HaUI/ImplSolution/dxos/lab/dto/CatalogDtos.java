// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.dto;

import HaUI.ImplSolution.dxos.lab.model.Device;
import HaUI.ImplSolution.dxos.lab.model.DeviceStatus;
import HaUI.ImplSolution.dxos.lab.model.Room;
import HaUI.ImplSolution.dxos.lab.model.RoomStatus;
import HaUI.ImplSolution.dxos.lab.model.RoomType;
import HaUI.ImplSolution.dxos.lab.model.SlaPolicy;
import HaUI.ImplSolution.dxos.lab.model.TicketCategory;
import HaUI.ImplSolution.dxos.lab.model.TicketPriority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Requests and responses of the catalogues: rooms, devices, ticket categories and SLA policies.
public final class CatalogDtos {

  public record RoomRequest(@NotBlank @Size(max = 32) String code,
      @NotBlank @Size(max = 64) String building, @NotBlank @Size(max = 255) String name,
      @NotNull RoomType roomType, @NotNull @Min(1) @Max(1000) Integer capacity) {}

  public record RoomResponse(Long id, String code, String building, String name, RoomType roomType,
      Integer capacity, RoomStatus status) {

    public static RoomResponse of(Room room) {
      return new RoomResponse(room.getId(), room.getCode(), room.getBuilding(), room.getName(),
          room.getRoomType(), room.getCapacity(), room.getStatus());
    }
  }

  public record RoomStatusRequest(@NotNull RoomStatus status) {}

  // The room after the change and the live bookings that now sit in a room under maintenance.
  public record RoomStatusChangeResponse(RoomResponse room,
      List<BookingDtos.BookingResponse> affectedBookings) {}

  public record DeviceRequest(@NotBlank @Size(max = 64) String assetCode,
      @NotBlank @Size(max = 64) String deviceType, @Size(max = 255) String model,
      @Size(max = 128) String serialNumber, Long roomId, LocalDate purchasedOn,
      LocalDate warrantyUntil) {}

  public record DeviceResponse(Long id, String assetCode, String deviceType, String model,
      String serialNumber, Long roomId, String roomCode, DeviceStatus status, LocalDate purchasedOn,
      LocalDate warrantyUntil) {

    public static DeviceResponse of(Device device) {
      Room room = device.getRoom();
      return new DeviceResponse(device.getId(), device.getAssetCode(), device.getDeviceType(),
          device.getModel(), device.getSerialNumber(), room == null ? null : room.getId(),
          room == null ? null : room.getCode(), device.getStatus(), device.getPurchasedOn(),
          device.getWarrantyUntil());
    }
  }

  public record TicketCategoryRequest(@NotBlank @Size(max = 48) String code,
      @NotBlank @Size(max = 255) String name, @NotNull TicketPriority defaultPriority,
      @Size(max = 1000) String keywords, Boolean active) {}

  public record TicketCategoryResponse(Long id, String code, String name,
      TicketPriority defaultPriority, String keywords, boolean active) {

    public static TicketCategoryResponse of(TicketCategory category) {
      return new TicketCategoryResponse(category.getId(), category.getCode(), category.getName(),
          category.getDefaultPriority(), category.getKeywords(), category.isActive());
    }
  }

  public record SlaPolicyRequest(@NotNull @Min(1) Integer responseMinutes,
      @NotNull @Min(1) Integer resolutionMinutes) {}

  public record SlaPolicyResponse(TicketPriority priority, Integer responseMinutes,
      Integer resolutionMinutes, Instant updatedAt) {

    public static SlaPolicyResponse of(SlaPolicy policy) {
      return new SlaPolicyResponse(policy.getPriority(), policy.getResponseMinutes(),
          policy.getResolutionMinutes(), policy.getUpdatedAt());
    }
  }

  private CatalogDtos() {}
}
