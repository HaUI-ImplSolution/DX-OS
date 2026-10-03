package HaUI.ImplSolution.DX_Farm.core.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, Long> {

  List<WorkflowEvent> findByWorkflowCaseIdOrderByOccurredAtAscIdAsc(Long workflowCaseId);
}
