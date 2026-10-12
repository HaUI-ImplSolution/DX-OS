// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

// Where a notification goes. Each channel has its own webhook; one without a webhook uses the
// default one.
public enum MattermostChannel {
  // New parcel and crop season requests for the administrators
  ADMIN,
  // New reports and pending diagnoses for the engineers
  ENGINEER
}
