package HaUI.ImplSolution.DX_Farm.dto;

import java.util.List;

// @formatter:off
public record ImagePrecheckResponse(
  boolean passed,
  List<String> reasons,
  int width,
  int height,
  double sharpness,
  double brightness
) {}
