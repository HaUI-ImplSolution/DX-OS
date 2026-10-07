package HaUI.ImplSolution.DX_Farm.dto;

import java.util.List;

// @formatter:off
public record ImagePrecheckResponse(
  // False when the photo is too small, too dark, too bright or too blurry
  boolean passed,

  // Farmer-facing reasons the photo was rejected; empty when passed
  List<String> reasons,

  // Pixel size of the photo
  int width,
  int height,

  // Measured values behind the verdict
  double sharpness,
  double brightness
) {}
