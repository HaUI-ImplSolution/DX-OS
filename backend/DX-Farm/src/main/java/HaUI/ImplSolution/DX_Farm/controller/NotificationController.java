// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.controller;

import HaUI.ImplSolution.DX_Farm.dto.ApiResponse;
import HaUI.ImplSolution.DX_Farm.dto.NotificationResponse;
import HaUI.ImplSolution.DX_Farm.dto.PageResponse;
import HaUI.ImplSolution.DX_Farm.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "In-app inbox of the signed-in account")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  @Operation(summary = "List my notifications, newest first",
      description = "Only the signed-in account's own messages. unreadOnly=true hides the ones"
          + " already read. size is capped at 100.")
  public ApiResponse<PageResponse<NotificationResponse>> list(
      @RequestParam(defaultValue = "false") boolean unreadOnly,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(notificationService.listMine(unreadOnly, page, size));
  }

  @GetMapping("/unread-count")
  @Operation(summary = "Number of my unread notifications")
  public ApiResponse<Map<String, Long>> unreadCount() {
    return ApiResponse.success(Map.of("unread", notificationService.unreadCount()));
  }

  @PatchMapping("/{id}/read")
  @Operation(summary = "Mark one notification as read",
      description = "404 for a notification that belongs to another account.")
  public ApiResponse<NotificationResponse> markRead(@PathVariable Long id) {
    return ApiResponse.success("Notification read", notificationService.markRead(id));
  }

  @PatchMapping("/read-all")
  @Operation(summary = "Mark all my notifications as read")
  public ApiResponse<Map<String, Integer>> markAllRead() {
    return ApiResponse.success("Notifications read",
        Map.of("updated", notificationService.markAllRead()));
  }
}
