// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceMemberRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

  List<WorkspaceMember> findByWorkspaceIdOrderByIdAsc(Long workspaceId);

  List<WorkspaceMember> findByWorkspaceIdInAndRole(Collection<Long> workspaceIds,
      WorkspaceMemberRole role);

  List<WorkspaceMember> findByUserIdOrderByIdAsc(String userId);

  List<WorkspaceMember> findByUserIdAndRole(String userId, WorkspaceMemberRole role);

  Optional<WorkspaceMember> findByWorkspaceIdAndRole(Long workspaceId, WorkspaceMemberRole role);

  Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, String userId);
}
