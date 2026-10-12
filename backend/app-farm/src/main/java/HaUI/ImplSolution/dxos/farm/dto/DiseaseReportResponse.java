// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import java.time.Instant;
import java.util.List;

// @formatter:off
public record DiseaseReportResponse(
  // Primary key
  Long id,

  // Crop season the report belongs to
  Long cropSeasonId,

  // Download link of the uploaded photo, valid for a short time only
  String imageUrl,

  // Name of the reporter (the farmer)
  String reportedBy,

  // When the report was recorded
  Instant reportedAt,

  // Current diagnosis of this report (null until one is recorded)
  DiagnosisResponse diagnosis,

  // PENDING, PROCESSED (a diagnosis exists) or NEEDS_RETAKE (an engineer asked for a new photo)
  String status,

  // Why a new photo is needed (only set when status is NEEDS_RETAKE)
  String retakeReason,

  // Report this photo replaces (null unless it is a retake)
  Long retakeOf,

  // Reports that replaced this one
  List<Long> retakeIds,

  // Photo quality measured when the report was submitted (null for older reports)
  ImagePrecheckResponse quality
) {}
