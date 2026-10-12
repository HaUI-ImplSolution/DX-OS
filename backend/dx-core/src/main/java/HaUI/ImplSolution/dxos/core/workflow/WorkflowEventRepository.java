// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, Long> {

  List<WorkflowEvent> findByWorkflowCaseIdOrderByOccurredAtAscIdAsc(Long workflowCaseId);
}
