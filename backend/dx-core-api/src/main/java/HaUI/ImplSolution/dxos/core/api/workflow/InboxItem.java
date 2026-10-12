// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceView;

// A case with the description of its entity and the workspace it belongs to (null when unknown).
public record InboxItem(CaseView workflowCase,CaseSummary summary,WorkspaceView workspace){}
