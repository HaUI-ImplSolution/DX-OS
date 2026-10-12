// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowActor;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import HaUI.ImplSolution.dxos.lab.LabSecurityPolicies;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

// The signed-in user as Lab sees them: identity from the core plus what each Lab role may see.
@Component
public class LabUser {

  private final UserContext userContext;

  public LabUser(UserContext userContext) {
    this.userContext = userContext;
  }

  public String id() {
    return userContext.currentUserId();
  }

  public String name() {
    return userContext.currentFullName();
  }

  public Set<String> roles() {
    return userContext.roles();
  }

  public Optional<WorkspaceView> workspace() {
    return userContext.currentWorkspace();
  }

  public Long workspaceId() {
    return workspace().map(WorkspaceView::id).orElse(null);
  }

  // Technician or administrator: works on requests of other people and sees all of them.
  public boolean isStaff() {
    return userContext.hasRole(LabSecurityPolicies.TECHNICIAN)
        || userContext.hasRole(LabSecurityPolicies.ADMIN);
  }

  public boolean isAdmin() {
    return userContext.hasRole(LabSecurityPolicies.ADMIN);
  }

  public boolean isTechnician() {
    return userContext.hasRole(LabSecurityPolicies.TECHNICIAN);
  }

  public boolean isLecturer() {
    return userContext.hasRole(LabSecurityPolicies.LECTURER);
  }

  // Lecturers approve loans of students, so they see every loan like staff does.
  public boolean seesAllLoans() {
    return isStaff() || isLecturer();
  }

  // The most privileged Lab role the user holds, used to decide who approves their requests.
  public String highestRole() {
    if (isAdmin()) {
      return LabSecurityPolicies.ADMIN;
    }
    if (isTechnician()) {
      return LabSecurityPolicies.TECHNICIAN;
    }
    if (isLecturer()) {
      return LabSecurityPolicies.LECTURER;
    }
    return LabSecurityPolicies.STUDENT;
  }

  // The roles travel with the actor so the workflow checks them against the step taken.
  public WorkflowActor actor() {
    return new WorkflowActor(id(), name(), roles());
  }
}
