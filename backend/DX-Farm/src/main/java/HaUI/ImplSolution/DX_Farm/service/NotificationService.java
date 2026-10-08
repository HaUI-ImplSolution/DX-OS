// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.identity.Household;
import HaUI.ImplSolution.DX_Farm.dto.NotificationResponse;
import HaUI.ImplSolution.DX_Farm.dto.PageResponse;
import HaUI.ImplSolution.DX_Farm.exception.ResourceNotFoundException;
import HaUI.ImplSolution.DX_Farm.model.Notification;
import HaUI.ImplSolution.DX_Farm.model.NotificationType;
import HaUI.ImplSolution.DX_Farm.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// In-app inbox. Messages are written in the transaction of the action they report, so a message
// exists exactly when the action happened.
@Service
@Transactional(readOnly = true)
public class NotificationService {

  private static final int MAX_PAGE_SIZE = 100;

  private final NotificationRepository notificationRepository;
  private final CurrentUserService currentUserService;
  private final Clock clock;

  public NotificationService(NotificationRepository notificationRepository,
      CurrentUserService currentUserService, Clock clock) {
    this.notificationRepository = notificationRepository;
    this.currentUserService = currentUserService;
    this.clock = clock;
  }

  // A household whose account is not linked yet has nobody to tell.
  @Transactional
  public void notifyHousehold(Household household, NotificationType type, String title, String body,
      String entityType, String entityRef) {
    if (household == null || household.getKeycloakUserId() == null) {
      return;
    }
    notificationRepository.save(Notification.builder()
        .recipientUserId(household.getKeycloakUserId()).type(type).title(title).body(body)
        .relatedEntityType(entityType).relatedEntityRef(entityRef).build());
  }

  // Creates the message unless one with the same key exists; returns whether it was created.
  @Transactional
  public boolean notifyHouseholdOnce(String dedupKey, Household household, NotificationType type,
      String title, String body, String entityType, String entityRef) {
    if (household == null || household.getKeycloakUserId() == null
        || notificationRepository.existsByDedupKey(dedupKey)) {
      return false;
    }
    notificationRepository.save(Notification.builder()
        .recipientUserId(household.getKeycloakUserId()).type(type).title(title).body(body)
        .relatedEntityType(entityType).relatedEntityRef(entityRef).dedupKey(dedupKey).build());
    return true;
  }

  public PageResponse<NotificationResponse> listMine(boolean unreadOnly, int page, int size) {
    String userId = currentUserService.currentUserId();
    PageRequest pageRequest =
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    Page<Notification> result = unreadOnly
        ? notificationRepository
            .findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(userId, pageRequest)
        : notificationRepository.findByRecipientUserIdOrderByCreatedAtDescIdDesc(userId,
            pageRequest);
    return PageResponse.of(result, NotificationService::toResponse);
  }

  public long unreadCount() {
    return notificationRepository
        .countByRecipientUserIdAndReadAtIsNull(currentUserService.currentUserId());
  }

  @Transactional
  public NotificationResponse markRead(Long id) {
    Notification notification =
        notificationRepository.findByIdAndRecipientUserId(id, currentUserService.currentUserId())
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
    if (notification.getReadAt() == null) {
      notification.setReadAt(Instant.now(clock));
      notificationRepository.save(notification);
    }
    return toResponse(notification);
  }

  @Transactional
  public int markAllRead() {
    return notificationRepository.markAllRead(currentUserService.currentUserId(),
        Instant.now(clock));
  }

  static NotificationResponse toResponse(Notification notification) {
    return new NotificationResponse(notification.getId(), notification.getType().name(),
        notification.getTitle(), notification.getBody(), notification.getRelatedEntityType(),
        notification.getRelatedEntityRef(), notification.getReadAt(), notification.getCreatedAt());
  }
}
