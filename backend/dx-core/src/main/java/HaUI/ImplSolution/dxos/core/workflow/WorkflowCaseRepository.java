// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.workflow;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkflowCaseRepository
    extends JpaRepository<WorkflowCase, Long>, JpaSpecificationExecutor<WorkflowCase> {

  long countByCreatedByAndCaseTypeAndStatusIn(String createdBy, String caseType,
      Collection<String> statuses);

  boolean existsByCaseTypeAndEntityTypeAndEntityIdInAndStatusIn(String caseType, String entityType,
      Collection<Long> entityIds, Collection<String> statuses);

  Optional<WorkflowCase> findByCaseTypeAndEntityTypeAndEntityId(String caseType, String entityType,
      Long entityId);
}
