// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.notification;

// Writes messages to the in-app inbox. A message is stored in the transaction of the action it
// reports, so it exists exactly when the action happened.
public interface NotificationPublisher {

  // type must be one the running app registered as a NotificationTypeDefinition. entityType and
  // entityRef tell the client which entity the message is about. A null userId has nobody to tell
  // and writes nothing.
  void notify(String userId, String type, String title, String body, String entityType,
      String entityRef);

  // Writes the message unless one with the same dedupKey exists; returns whether it was written.
  boolean notifyOnce(String dedupKey, String userId, String type, String title, String body,
      String entityType, String entityRef);
}
