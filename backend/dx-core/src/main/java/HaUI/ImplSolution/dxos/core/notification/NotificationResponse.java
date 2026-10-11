// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.notification;

import java.time.Instant;

// @formatter:off
public record NotificationResponse(
  Long id,

  // A type the running app registered, e.g. REQUEST_APPROVED
  String type,

  String title,

  String body,

  // Entity the message is about: its type and reference in the app
  String relatedEntityType,
  String relatedEntityRef,

  // Null while unread
  Instant readAt,

  Instant createdAt
){}
