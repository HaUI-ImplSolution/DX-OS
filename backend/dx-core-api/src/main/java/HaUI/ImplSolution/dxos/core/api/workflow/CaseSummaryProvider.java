// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import java.util.Collection;
import java.util.List;
import java.util.Map;

// Describes the cases of one entity type for the approval inbox. The inbox collects the ids of a
// page of cases and asks once per entity type, so an implementation loads them in one query.
public interface CaseSummaryProvider {

  // The entity type the provider describes, as stored on the case.
  String entityType();

  // Keyed by entity id; entities that no longer exist are absent.
  Map<Long, CaseSummary> summarize(Collection<Long> entityIds);

  // Ids of the entities of this type that belong to the workspace.
  List<Long> entityIdsOfWorkspace(Long workspaceId);
}
