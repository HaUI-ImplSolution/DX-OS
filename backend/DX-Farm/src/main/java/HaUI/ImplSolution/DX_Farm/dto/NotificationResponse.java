package HaUI.ImplSolution.DX_Farm.dto;

import java.time.Instant;

// @formatter:off
public record NotificationResponse(
  Long id,

  // PARCEL_APPROVED, CROP_SEASON_REJECTED, HARVEST_REMINDER, ...
  String type,

  String title,

  String body,

  // Entity the message is about: PARCEL (parcel code), CROP_SEASON (season id) or DISEASE_REPORT
  // (report id)
  String relatedEntityType,
  String relatedEntityRef,

  // Null while unread
  Instant readAt,

  Instant createdAt
){}
