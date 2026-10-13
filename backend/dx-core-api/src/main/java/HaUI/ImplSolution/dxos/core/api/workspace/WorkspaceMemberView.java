// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workspace;

import java.time.Instant;

// userId: token "sub" of the account.
public record WorkspaceMemberView(Long workspaceId,String userId,WorkspaceMemberRole role,Instant createdAt){}
