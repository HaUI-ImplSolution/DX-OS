// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

// Stops the signed-in user from piling up requests nobody has decided yet.
public interface PendingRequestLimit {

  // Throws when the user already has the maximum number of waiting requests.
  void requireCapacity();
}
