// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workspace;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

// Lookup of the workspaces an application attaches its data to.
public interface WorkspaceDirectory {

  Optional<WorkspaceView> findById(Long id);

  Optional<WorkspaceView> findByCode(String code);

  // The workspace linked to the account with this token "sub".
  Optional<WorkspaceView> findByAccount(String userId);

  // Keyed by workspace id; unknown ids are absent.
  Map<Long, WorkspaceView> findByIds(Collection<Long> ids);
}
