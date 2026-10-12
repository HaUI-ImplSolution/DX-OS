// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.notification.NotificationPublisher;
import org.springframework.stereotype.Component;

// Writes the in-app messages of the Lab app. A message is stored in the transaction of the action
// it reports.
@Component
public class LabNotifier {

  private final NotificationPublisher publisher;

  public LabNotifier(NotificationPublisher publisher) {
    this.publisher = publisher;
  }

  public void tell(String userId, String type, String title, String body, String entityType,
      Long entityId) {
    publisher.notify(userId, type, title, body, entityType, String.valueOf(entityId));
  }

  // Skips the message when one with the same key was already written.
  public boolean tellOnce(String dedupKey, String userId, String type, String title, String body,
      String entityType, Long entityId) {
    return publisher.notifyOnce(dedupKey, userId, type, title, body, entityType,
        String.valueOf(entityId));
  }
}
