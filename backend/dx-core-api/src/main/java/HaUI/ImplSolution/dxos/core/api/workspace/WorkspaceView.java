// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workspace;

// keycloakUserId: token "sub" of the linked account; null while no account is linked.
public record WorkspaceView(Long id,String code,String fullName,String keycloakUserId){}
