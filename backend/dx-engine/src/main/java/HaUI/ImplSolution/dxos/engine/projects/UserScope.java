// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.projects;

import HaUI.ImplSolution.dxos.core.api.user.UserContext;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

// What the signed-in person may administer. The platform administrator reaches everything. Anyone
// else reaches only the projects where they are an administrator, read fresh on every request so a
// change of level takes effect at once. A model that belongs to no project is reached by the
// platform administrator alone, and a person with no scope at all is refused.
@Component
public class UserScope {

  private final UserContext user;
  private final ProjectMembers members;
  private final ProjectLinks links;

  public UserScope(UserContext user, ProjectMembers members, ProjectLinks links) {
    this.user = user;
    this.members = members;
    this.links = links;
  }

  public Scope current() {
    if (user.hasRole("ADMIN")) {
      return new Scope(true, Set.of(), Map.of(), members);
    }
    Set<String> projects = members.administered(user.currentUserId());
    Map<String, String> models = projects.isEmpty() ? Map.of() : links.ofModels();
    return new Scope(false, projects, models, members);
  }

  public static final class Scope {

    private final boolean global;
    private final Set<String> projects;
    private final Map<String, String> modelProjects;
    private final ProjectMembers members;

    private Scope(
        boolean global,
        Set<String> projects,
        Map<String, String> modelProjects,
        ProjectMembers members) {
      this.global = global;
      this.projects = projects;
      this.modelProjects = modelProjects;
      this.members = members;
    }

    public boolean global() {
      return global;
    }

    // The projects administered; empty for the platform administrator, who needs no list.
    public Set<String> projects() {
      return projects;
    }

    public void requireAny() {
      if (!global && projects.isEmpty()) {
        throw denied();
      }
    }

    public void requireGlobal() {
      if (!global) {
        throw denied();
      }
    }

    public boolean coversProject(String project) {
      return global || projects.contains(project);
    }

    public void requireProject(String project) {
      if (!coversProject(project)) {
        throw denied();
      }
    }

    public boolean coversModel(String model) {
      if (global) {
        return true;
      }
      String project = modelProjects.get(model);
      return project != null && projects.contains(project);
    }

    public void requireModel(String model) {
      if (!coversModel(model)) {
        throw denied();
      }
    }

    // Whether the account belongs to a project in the scope.
    public boolean coversUser(String userId) {
      return global || members.isMemberOfAny(projects, userId);
    }

    public void requireUser(String userId) {
      if (!coversUser(userId)) {
        throw denied();
      }
    }

    private static AccessDeniedException denied() {
      return new AccessDeniedException("Outside the projects this account administers");
    }
  }
}
