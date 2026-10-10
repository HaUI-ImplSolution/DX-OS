// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

// Filters of a case search; null fields are not applied.
// unassigned: only cases nobody has taken. createdFrom is inclusive, createdBefore exclusive.
// anyOfEntities: a case matches when it tracks one of the listed entities; an empty list (or
// only empty id sets) matches nothing.
public record CaseQuery(String caseType,String status,String assignedTo,boolean unassigned,Instant createdFrom,Instant createdBefore,List<EntityIds>anyOfEntities){

public record EntityIds(String entityType,Collection<Long>ids){}}
