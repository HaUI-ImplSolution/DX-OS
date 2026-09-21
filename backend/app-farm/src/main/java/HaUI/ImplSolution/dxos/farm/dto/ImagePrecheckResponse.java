// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import java.util.List;

// @formatter:off
public record ImagePrecheckResponse(
  // False when the photo is too small, too dark, too bright, too blurry or not accepted as rice
  boolean passed,

  // Farmer-facing reasons the photo was rejected; empty when passed
  List<String> reasons,

  // Pixel size of the photo
  int width,
  int height,

  // Image quality measurements (not crop classifier scores)
  double sharpness,
  double brightness
) {}
