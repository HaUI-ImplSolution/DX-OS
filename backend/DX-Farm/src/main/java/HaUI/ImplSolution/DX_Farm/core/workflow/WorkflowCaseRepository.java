// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.core.workflow;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkflowCaseRepository
    extends JpaRepository<WorkflowCase, Long>, JpaSpecificationExecutor<WorkflowCase> {

  long countByCreatedByAndStatusAndCaseTypeIn(String createdBy, WorkflowStatus status,
      Collection<String> caseTypes);

  boolean existsByCaseTypeAndEntityTypeAndEntityIdInAndStatus(String caseType, String entityType,
      Collection<Long> entityIds, WorkflowStatus status);

  Optional<WorkflowCase> findByCaseTypeAndEntityTypeAndEntityId(String caseType, String entityType,
      Long entityId);
}
