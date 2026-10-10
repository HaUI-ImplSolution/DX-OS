// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

// @formatter:off
public record WorkspaceCreateResponse(
  WorkspaceResponse workspace,

  // Username of the created account; null when none was created
  String username,

  // Returned once and changed at first login; null when no account was created
  String temporaryPassword
){}
