// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberRole;
import java.time.Instant;

// @formatter:off
public record WorkspaceMemberResponse(
  // Keycloak user id (token claim "sub")
  String userId,

  WorkspaceMemberRole role,

  Instant createdAt
){}
