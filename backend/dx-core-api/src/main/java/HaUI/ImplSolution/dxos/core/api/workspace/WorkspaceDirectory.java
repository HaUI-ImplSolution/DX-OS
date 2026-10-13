// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workspace;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Lookup of the workspaces an application attaches its data to.
public interface WorkspaceDirectory {

  Optional<WorkspaceView> findById(Long id);

  Optional<WorkspaceView> findByCode(String code);

  // The workspace the account owns, else the first one it belongs to.
  Optional<WorkspaceView> findByAccount(String userId);

  // Every workspace the account belongs to, oldest membership first.
  List<WorkspaceView> findAllByAccount(String userId);

  // Keyed by workspace id; unknown ids are absent.
  Map<Long, WorkspaceView> findByIds(Collection<Long> ids);

  // All workspaces of a type ordered by code; every type when type is null.
  List<WorkspaceView> findAll(String type);

  List<WorkspaceMemberView> members(Long workspaceId);
}
