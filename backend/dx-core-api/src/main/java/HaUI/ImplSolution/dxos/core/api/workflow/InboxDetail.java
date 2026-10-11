// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import java.util.List;

// One inbox item with its timeline, oldest event first.
public record InboxDetail(InboxItem item,List<WorkflowEventResponse>events){}
