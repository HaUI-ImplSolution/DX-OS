package HaUI.ImplSolution.DX_Farm.core.workflow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkflowCaseRepository
    extends JpaRepository<WorkflowCase, Long>, JpaSpecificationExecutor<WorkflowCase> {

  Optional<WorkflowCase> findByCaseTypeAndEntityTypeAndEntityId(String caseType, String entityType,
      Long entityId);
}
