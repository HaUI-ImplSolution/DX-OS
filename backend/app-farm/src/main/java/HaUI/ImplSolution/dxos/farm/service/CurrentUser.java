// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import HaUI.ImplSolution.dxos.farm.model.Parcel;
import java.util.Optional;
import org.springframework.stereotype.Component;

// The signed-in user as Farm sees them: identity from the core plus access to parcels.
@Component
public class CurrentUser {

  private final UserContext userContext;

  public CurrentUser(UserContext userContext) {
    this.userContext = userContext;
  }

  public String currentUserId() {
    return userContext.currentUserId();
  }

  public String currentFullName() {
    return userContext.currentFullName();
  }

  // The household linked to the signed-in account, if an administrator has linked one.
  public Optional<WorkspaceView> currentWorkspace() {
    return userContext.currentWorkspace();
  }

  // A FARMER without ENGINEER/ADMIN is limited to their own household's parcels and approved
  // diagnoses.
  public boolean isFarmer() {
    return userContext.isFarmer();
  }

  public boolean isAdmin() {
    return userContext.isAdmin();
  }

  // A farmer only accesses parcels of their own household; other roles access all.
  public boolean canAccess(Parcel parcel) {
    if (!isFarmer()) {
      return true;
    }
    return currentWorkspace().map(workspace -> workspace.id().equals(parcel.getHouseholdId()))
        .orElse(false);
  }
}
