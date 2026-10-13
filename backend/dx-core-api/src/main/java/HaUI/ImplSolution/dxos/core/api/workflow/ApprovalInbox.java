// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;

// The list of requests waiting for a decision, shared by every app. Cases are described through
// the CaseSummaryProvider of their entity type.
public interface ApprovalInbox {

  // Oldest first; size is capped at 100.
  PageResponse<InboxItem> list(InboxQuery query, int page, int size);

  // 404 for an unknown case id.
  InboxDetail detail(Long caseId);
}
