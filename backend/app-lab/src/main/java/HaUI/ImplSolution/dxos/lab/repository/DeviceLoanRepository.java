// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.repository;

import HaUI.ImplSolution.dxos.lab.model.DeviceLoan;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeviceLoanRepository
    extends JpaRepository<DeviceLoan, Long>, JpaSpecificationExecutor<DeviceLoan> {

  @Override
  @EntityGraph(attributePaths = {"device"})
  Page<DeviceLoan> findAll(Specification<DeviceLoan> spec, Pageable pageable);

  Optional<DeviceLoan> findByClientRequestId(UUID clientRequestId);

  List<DeviceLoan> findByIdIn(Collection<Long> ids);

  List<DeviceLoan> findByWorkspaceId(Long workspaceId);

  // Loans a person still holds a claim on: waiting for a decision or not yet returned.
  long countByBorrowerIdAndStatusIn(String borrowerId, Collection<String> statuses);

  boolean existsByDevice_IdAndStatusIn(Long deviceId, Collection<String> statuses);

  List<DeviceLoan> findByStatusAndDueDateLessThanEqual(String status, LocalDate dueDate);
}
