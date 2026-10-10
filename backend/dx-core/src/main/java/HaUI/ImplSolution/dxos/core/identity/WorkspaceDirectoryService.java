// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberRole;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberView;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkspaceDirectoryService implements WorkspaceDirectory {

  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository memberRepository;

  public WorkspaceDirectoryService(WorkspaceRepository workspaceRepository,
      WorkspaceMemberRepository memberRepository) {
    this.workspaceRepository = workspaceRepository;
    this.memberRepository = memberRepository;
  }

  @Override
  public Optional<WorkspaceView> findById(Long id) {
    return workspaceRepository.findById(id).map(this::toView);
  }

  @Override
  public Optional<WorkspaceView> findByCode(String code) {
    return workspaceRepository.findByCode(code).map(this::toView);
  }

  @Override
  public Optional<WorkspaceView> findByAccount(String userId) {
    List<WorkspaceMember> memberships = memberRepository.findByUserIdOrderByIdAsc(userId);
    return memberships.stream().filter(member -> member.getRole() == WorkspaceMemberRole.OWNER)
        .findFirst().or(() -> memberships.stream().findFirst())
        .flatMap(member -> workspaceRepository.findById(member.getWorkspaceId())).map(this::toView);
  }

  @Override
  public List<WorkspaceView> findAllByAccount(String userId) {
    List<Long> ids = memberRepository.findByUserIdOrderByIdAsc(userId).stream()
        .map(WorkspaceMember::getWorkspaceId).toList();
    Map<Long, WorkspaceView> byId = findByIds(ids);
    return ids.stream().map(byId::get).filter(view -> view != null).toList();
  }

  @Override
  public Map<Long, WorkspaceView> findByIds(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return toViews(workspaceRepository.findAllById(ids)).stream()
        .collect(Collectors.toMap(WorkspaceView::id, Function.identity()));
  }

  @Override
  public List<WorkspaceView> findAll(String type) {
    List<Workspace> workspaces = type == null ? workspaceRepository.findAllByOrderByCodeAsc()
        : workspaceRepository.findAllByTypeOrderByCodeAsc(type);
    return toViews(workspaces);
  }

  @Override
  public List<WorkspaceMemberView> members(Long workspaceId) {
    return memberRepository.findByWorkspaceIdOrderByIdAsc(workspaceId).stream()
        .map(WorkspaceDirectoryService::toMemberView).toList();
  }

  WorkspaceView toView(Workspace workspace) {
    String ownerUserId =
        memberRepository.findByWorkspaceIdAndRole(workspace.getId(), WorkspaceMemberRole.OWNER)
            .map(WorkspaceMember::getUserId).orElse(null);
    return view(workspace, ownerUserId);
  }

  // One owner query for the whole list.
  private List<WorkspaceView> toViews(List<Workspace> workspaces) {
    if (workspaces.isEmpty()) {
      return List.of();
    }
    Map<Long, String> owners = new HashMap<>();
    memberRepository
        .findByWorkspaceIdInAndRole(workspaces.stream().map(Workspace::getId).toList(),
            WorkspaceMemberRole.OWNER)
        .forEach(member -> owners.put(member.getWorkspaceId(), member.getUserId()));
    return workspaces.stream().map(workspace -> view(workspace, owners.get(workspace.getId())))
        .toList();
  }

  static WorkspaceView view(Workspace workspace, String ownerUserId) {
    Map<String, String> attributes = workspace.getAttributes() == null ? Map.of()
        : new LinkedHashMap<>(workspace.getAttributes());
    return new WorkspaceView(workspace.getId(), workspace.getCode(), workspace.getType(),
        workspace.getName(), workspace.getPhone(), workspace.getAddress(), ownerUserId, attributes,
        workspace.getCreatedAt());
  }

  static WorkspaceMemberView toMemberView(WorkspaceMember member) {
    return new WorkspaceMemberView(member.getWorkspaceId(), member.getUserId(), member.getRole(),
        member.getCreatedAt());
  }
}
