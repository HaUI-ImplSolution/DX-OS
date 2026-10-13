// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workspace;

// username and temporaryPassword are null when no account was created. The temporary password is
// returned once and must be changed at first login.
public record WorkspaceCreated(WorkspaceView workspace,String username,String temporaryPassword){}
