// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.status;

// A service the app depends on and wants shown in the system status.
public interface HealthContributor {

  // Name shown in the status, e.g. ai.
  String name();

  boolean isUp();
}
