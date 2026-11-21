// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.projects;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.engine.clients.AppClientService.AppView;
import HaUI.ImplSolution.dxos.engine.projects.ProjectService.ChangedProject;
import HaUI.ImplSolution.dxos.engine.projects.ProjectService.Link;
import HaUI.ImplSolution.dxos.engine.projects.ProjectService.MemberLevel;
import HaUI.ImplSolution.dxos.engine.projects.ProjectService.MemberView;
import HaUI.ImplSolution.dxos.engine.projects.ProjectService.Mine;
import HaUI.ImplSolution.dxos.engine.projects.ProjectService.ModelLink;
import HaUI.ImplSolution.dxos.engine.projects.ProjectService.NewProject;
import HaUI.ImplSolution.dxos.engine.projects.ProjectService.ProjectView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("isAuthenticated()")
@Tag(
    name = "Projects",
    description =
        "Group the models and outside applications that one integration uses; ADMIN only, except"
            + " the members and the scope of the caller. A project is for browsing and judging"
            + " impact, it grants no access and isolates nothing; its administrators manage its"
            + " members and the roles of its models.")
public class ProjectController {

  private final ProjectService service;

  public ProjectController(ProjectService service) {
    this.service = service;
  }

  @GetMapping("/projects")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "The projects with the models and applications in each",
      description =
          "A model or an application that belongs to no project is not listed here; its project"
              + " is null in the model and application lists.")
  public ApiResponse<List<ProjectView>> list() {
    return ApiResponse.success(service.list());
  }

  @PostMapping("/projects")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Create a project",
      description =
          "The name matches [a-z][a-z0-9_-]* and is at most 40 characters; the label defaults to"
              + " the name. 409 when the name is taken.")
  public ApiResponse<ProjectView> create(@RequestBody NewProject request) {
    return ApiResponse.success(service.create(request));
  }

  @PutMapping("/projects/{name}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Change the label and the description of a project",
      description = "404 when the project does not exist.")
  public ApiResponse<ProjectView> update(
      @PathVariable String name, @RequestBody ChangedProject request) {
    return ApiResponse.success(service.update(name, request));
  }

  @DeleteMapping("/projects/{name}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Delete a project",
      description =
          "409 while the project still holds a model or an application; move them or set their"
              + " project to null first. 404 when the project does not exist.")
  public ApiResponse<Void> remove(@PathVariable String name) {
    service.remove(name);
    return ApiResponse.success(null);
  }

  @PutMapping("/models/{model}/project")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Put a model in a project",
      description =
          "A model belongs to at most one project; a null project makes it unclassified. 404 when"
              + " the model is not applied or the project does not exist.")
  public ApiResponse<ModelLink> setModelProject(
      @PathVariable String model, @RequestBody Link request) {
    return ApiResponse.success(service.setModelProject(model, request));
  }

  @PutMapping("/clients/{name}/project")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Put an outside application in a project",
      description =
          "An application belongs to at most one project, whichever project the model it calls is"
              + " in; a null project makes it unclassified. 404 when the application or the"
              + " project does not exist.")
  public ApiResponse<AppView> setClientProject(
      @PathVariable String name, @RequestBody Link request) {
    return ApiResponse.success(service.setClientProject(name, request));
  }

  @GetMapping("/projects/mine")
  @Operation(
      summary = "What the signed-in account administers",
      description =
          "global is true for the platform administrator, who reaches every project. projects"
              + " lists the projects the account is a member of, with its level. Any signed-in"
              + " account may call it.")
  public ApiResponse<Mine> mine() {
    return ApiResponse.success(service.mine());
  }

  @GetMapping("/projects/{name}/members")
  @Operation(
      summary = "The members of a project",
      description =
          "For the platform administrator and the administrators of that project; 403 for anyone"
              + " else, 404 when the project does not exist. A member whose account no longer"
              + " exists is left out.")
  public ApiResponse<List<MemberView>> members(@PathVariable String name) {
    return ApiResponse.success(service.members(name));
  }

  @PutMapping("/projects/{name}/members/{userId}")
  @Operation(
      summary = "Add an account to a project or change its level",
      description =
          "Body: {\"level\": \"MEMBER\"|\"ADMIN\"}. A member can be given the roles of the models of"
              + " the project; an administrator also manages the members. An administrator of the"
              + " project adds and removes members; only the platform administrator makes or"
              + " changes an administrator, otherwise 403. 400 when the level is wrong, 404 when"
              + " the project or the account does not exist.")
  public ApiResponse<MemberView> setMember(
      @PathVariable String name, @PathVariable String userId, @RequestBody MemberLevel request) {
    return ApiResponse.success(service.setMember(name, userId, request));
  }

  @DeleteMapping("/projects/{name}/members/{userId}")
  @Operation(
      summary = "Take an account out of a project",
      description =
          "The roles the account was given stay. Not being a member changes nothing. 403 when an"
              + " administrator of the project removes an administrator, 404 when the project does"
              + " not exist.")
  public ApiResponse<Void> removeMember(@PathVariable String name, @PathVariable String userId) {
    service.removeMember(name, userId);
    return ApiResponse.success(null);
  }
}
