// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.notification;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.core.api.notification.NotificationPublisher;
import HaUI.ImplSolution.dxos.core.api.user.UserContext;
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
public class NotificationInboxService implements NotificationPublisher {

  private static final int MAX_PAGE_SIZE = 100;

  private final NotificationRepository notificationRepository;
  private final NotificationTypes notificationTypes;
  private final UserContext userContext;
  private final Clock clock;

  public NotificationInboxService(NotificationRepository notificationRepository,
      NotificationTypes notificationTypes, UserContext userContext, Clock clock) {
    this.notificationRepository = notificationRepository;
    this.notificationTypes = notificationTypes;
    this.userContext = userContext;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void notify(String userId, String type, String title, String body, String entityType,
      String entityRef) {
    notificationTypes.require(type);
    if (userId == null) {
      return;
    }
    notificationRepository.save(Notification.builder().recipientUserId(userId).type(type)
        .title(title).body(body).relatedEntityType(entityType).relatedEntityRef(entityRef).build());
  }

  @Override
  @Transactional
  public boolean notifyOnce(String dedupKey, String userId, String type, String title, String body,
      String entityType, String entityRef) {
    notificationTypes.require(type);
    if (userId == null || notificationRepository.existsByDedupKey(dedupKey)) {
      return false;
    }
    notificationRepository
        .save(Notification.builder().recipientUserId(userId).type(type).title(title).body(body)
            .relatedEntityType(entityType).relatedEntityRef(entityRef).dedupKey(dedupKey).build());
    return true;
  }

  public PageResponse<NotificationResponse> listMine(boolean unreadOnly, int page, int size) {
    String userId = userContext.currentUserId();
    PageRequest pageRequest =
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    Page<Notification> result = unreadOnly
        ? notificationRepository
            .findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(userId, pageRequest)
        : notificationRepository.findByRecipientUserIdOrderByCreatedAtDescIdDesc(userId,
            pageRequest);
    return PageResponse.of(result, NotificationInboxService::toResponse);
  }

  public long unreadCount() {
    return notificationRepository
        .countByRecipientUserIdAndReadAtIsNull(userContext.currentUserId());
  }

  @Transactional
  public NotificationResponse markRead(Long id) {
    Notification notification =
        notificationRepository.findByIdAndRecipientUserId(id, userContext.currentUserId())
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
    if (notification.getReadAt() == null) {
      notification.setReadAt(Instant.now(clock));
      notificationRepository.save(notification);
    }
    return toResponse(notification);
  }

  @Transactional
  public int markAllRead() {
    return notificationRepository.markAllRead(userContext.currentUserId(), Instant.now(clock));
  }

  static NotificationResponse toResponse(Notification notification) {
    return new NotificationResponse(notification.getId(), notification.getType(),
        notification.getTitle(), notification.getBody(), notification.getRelatedEntityType(),
        notification.getRelatedEntityRef(), notification.getReadAt(), notification.getCreatedAt());
  }
}
