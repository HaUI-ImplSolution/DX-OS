// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workspace;

// Changes to workspaces and to the accounts that belong to them.
public interface WorkspaceAdministration {

  // Creates the workspace and, when the command names a username, its owner account in one
  // step: a failure creating the account leaves no workspace behind.
  WorkspaceCreated create(WorkspaceCreateCommand command);

  // Makes an existing account the owner of a workspace that has none. Name the account by
  // keycloakUserId or by username.
  WorkspaceView linkOwner(String code, String keycloakUserId, String username);

  // Adds an existing account as a member; a member that is already there gets the new role.
  WorkspaceMemberView addMember(String code, String keycloakUserId, String username,
      WorkspaceMemberRole role);
}
