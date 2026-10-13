// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.status;

import java.util.List;

// @formatter:off
public record ServiceStatusResponse(
  // The app this backend runs; null when no app module is installed
  App app,
  List<Service> services
) {

  public record App(String id, String name) {}

  public record Service(String name, String status) {}
}
