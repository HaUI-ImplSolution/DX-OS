// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.notification.NotificationPublisher;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import HaUI.ImplSolution.dxos.farm.model.NotificationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Tells the account that owns a household about what happened to its data.
@Service
@Transactional(readOnly = true)
public class NotificationService {

  private final NotificationPublisher notificationPublisher;
  private final WorkspaceDirectory workspaceDirectory;

  public NotificationService(NotificationPublisher notificationPublisher,
      WorkspaceDirectory workspaceDirectory) {
    this.notificationPublisher = notificationPublisher;
    this.workspaceDirectory = workspaceDirectory;
  }

  // A household whose account is not linked yet has nobody to tell.
  @Transactional
  public void notifyHousehold(Long householdId, NotificationType type, String title, String body,
      String entityType, String entityRef) {
    notificationPublisher.notify(recipientOf(householdId), type.name(), title, body, entityType,
        entityRef);
  }

  // Creates the message unless one with the same key exists; returns whether it was created.
  @Transactional
  public boolean notifyHouseholdOnce(String dedupKey, Long householdId, NotificationType type,
      String title, String body, String entityType, String entityRef) {
    return notificationPublisher.notifyOnce(dedupKey, recipientOf(householdId), type.name(), title,
        body, entityType, entityRef);
  }

  private String recipientOf(Long householdId) {
    if (householdId == null) {
      return null;
    }
    return workspaceDirectory.findById(householdId).map(WorkspaceView::ownerUserId).orElse(null);
  }
}
