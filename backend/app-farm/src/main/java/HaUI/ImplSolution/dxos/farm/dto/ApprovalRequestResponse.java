// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import java.time.Instant;
import java.util.Map;

// A workflow case together with the entity it tracks, as the management pages show it.
// @formatter:off
public record ApprovalRequestResponse(
  // Workflow case id: the id used to approve, reject, assign and read the timeline
  Long caseId,

  // PARCEL_APPROVAL, PARCEL_EDIT_REQUEST, CROP_SEASON_APPROVAL or DIAGNOSIS_APPROVAL
  String caseType,

  // PENDING, APPROVED, REJECTED or CANCELLED
  String status,

  // Business code or short label of the entity (parcel code, season code, ...)
  String title,

  // Household the entity belongs to
  String householdCode,
  String farmerName,

  // Entity values, keyed by field name; edit requests carry proposed.* and current.* pairs
  Map<String, Object> details,

  Instant createdAt,

  // Assignee (null while nobody has taken the case)
  String assignedTo,
  String assignedToName,

  Instant updatedAt
){}
