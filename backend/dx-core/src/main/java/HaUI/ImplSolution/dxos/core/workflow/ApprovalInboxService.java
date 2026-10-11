// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.workflow;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.workflow.ApprovalInbox;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseQuery;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummary;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseSummaryProvider;
import HaUI.ImplSolution.dxos.core.api.workflow.CaseView;
import HaUI.ImplSolution.dxos.core.api.workflow.InboxDetail;
import HaUI.ImplSolution.dxos.core.api.workflow.InboxItem;
import HaUI.ImplSolution.dxos.core.api.workflow.InboxQuery;
import HaUI.ImplSolution.dxos.core.api.workflow.WorkflowOperations;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceDirectory;
import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Workflow cases enriched with the entity behind them, filterable by workspace and day.
@Service
@Transactional(readOnly = true)
public class ApprovalInboxService implements ApprovalInbox {

  private final WorkflowOperations workflow;
  private final WorkspaceDirectory workspaceDirectory;
  private final Clock clock;
  private final Map<String, CaseSummaryProvider> providers = new HashMap<>();

  public ApprovalInboxService(WorkflowOperations workflow, WorkspaceDirectory workspaceDirectory,
      Clock clock, ObjectProvider<CaseSummaryProvider> summaryProviders) {
    this.workflow = workflow;
    this.workspaceDirectory = workspaceDirectory;
    this.clock = clock;
    summaryProviders.orderedStream()
        .forEach(provider -> providers.put(provider.entityType(), provider));
  }

  @Override
  public PageResponse<InboxItem> list(InboxQuery query, int page, int size) {
    Instant start =
        query.from() != null ? query.from().atStartOfDay(clock.getZone()).toInstant() : null;
    Instant end =
        query.to() != null ? query.to().plusDays(1).atStartOfDay(clock.getZone()).toInstant()
            : null;
    List<CaseQuery.EntityIds> entities = null;
    if (query.workspaceCode() != null && !query.workspaceCode().isBlank()) {
      entities = workspaceEntities(query.workspaceCode().strip());
    }
    PageResponse<CaseView> cases = workflow.search(new CaseQuery(query.caseType(), query.status(),
        query.assignedTo(), query.unassigned(), start, end, entities), page, size);
    Map<Long, InboxItem> byCase = toItems(cases.items());
    return cases.map(workflowCase -> byCase.get(workflowCase.id()));
  }

  @Override
  public InboxDetail detail(Long caseId) {
    CaseView workflowCase = workflow.get(caseId);
    InboxItem item = toItems(List.of(workflowCase)).get(caseId);
    return new InboxDetail(item, workflow.events(caseId));
  }

  // Entities that belong to the workspace; none match an unknown workspace.
  private List<CaseQuery.EntityIds> workspaceEntities(String workspaceCode) {
    Long workspaceId =
        workspaceDirectory.findByCode(workspaceCode).map(WorkspaceView::id).orElse(null);
    if (workspaceId == null) {
      return List.of();
    }
    List<CaseQuery.EntityIds> entities = new ArrayList<>();
    providers.values()
        .forEach(provider -> entities.add(new CaseQuery.EntityIds(provider.entityType(),
            provider.entityIdsOfWorkspace(workspaceId))));
    return entities;
  }

  // One summary query per entity type and one workspace query for the whole page.
  private Map<Long, InboxItem> toItems(List<CaseView> cases) {
    Map<String, List<Long>> idsByType =
        cases.stream().collect(Collectors.groupingBy(CaseView::entityType,
            Collectors.mapping(CaseView::entityId, Collectors.toList())));
    Map<String, Map<Long, CaseSummary>> summaries = new HashMap<>();
    idsByType.forEach((entityType, ids) -> {
      CaseSummaryProvider provider = providers.get(entityType);
      summaries.put(entityType, provider == null ? Map.of() : provider.summarize(ids));
    });
    Set<Long> workspaceIds = new HashSet<>();
    summaries.values().forEach(byId -> byId.values().forEach(summary -> {
      if (summary.workspaceId() != null) {
        workspaceIds.add(summary.workspaceId());
      }
    }));
    Map<Long, WorkspaceView> workspaces = workspaceDirectory.findByIds(workspaceIds);

    Map<Long, InboxItem> items = new HashMap<>();
    for (CaseView workflowCase : cases) {
      CaseSummary summary = summaries.get(workflowCase.entityType()).get(workflowCase.entityId());
      if (summary == null) {
        summary = new CaseSummary("#" + workflowCase.entityId(), null, null, Map.of());
      }
      WorkspaceView workspace =
          summary.workspaceId() != null ? workspaces.get(summary.workspaceId()) : null;
      items.put(workflowCase.id(), new InboxItem(workflowCase, summary, workspace));
    }
    return items;
  }
}
