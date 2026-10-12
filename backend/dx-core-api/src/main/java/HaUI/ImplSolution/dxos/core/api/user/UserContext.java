// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.user;

import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import java.util.Optional;

// The signed-in user of the current request, read from the verified token.
public interface UserContext {

  // Stable account id (token "sub"), unlike the display name which users can change.
  String currentUserId();

  String currentFullName();

  // The workspace linked to the signed-in account, if an administrator has linked one.
  Optional<WorkspaceView> currentWorkspace();

  // A FARMER without ENGINEER or ADMIN.
  boolean isFarmer();

  boolean isAdmin();
}
