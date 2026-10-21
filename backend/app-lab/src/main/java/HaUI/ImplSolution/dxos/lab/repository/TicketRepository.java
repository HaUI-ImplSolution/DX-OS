// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.repository;

import HaUI.ImplSolution.dxos.lab.model.Ticket;
import java.time.Instant;
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository
    extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

  // Loads what a ticket row shows in the same query as the page.
  @Override
  @EntityGraph(attributePaths = {"category", "room", "device"})
  Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

  Optional<Ticket> findByClientRequestId(UUID clientRequestId);

  List<Ticket> findByIdIn(Collection<Long> ids);

  List<Ticket> findByWorkspaceId(Long workspaceId);

  long countByDeviceIdAndCreatedAtGreaterThanEqual(Long deviceId, Instant from);

  // Open tickets whose resolution deadline passed and that were not marked overdue yet.
  @Query("""
      select t from Ticket t
      where t.overdue = false and t.status in :openStatuses and t.resolutionDueAt <= :now
      order by t.resolutionDueAt
      """)
  List<Ticket> findNewlyOverdue(@Param("openStatuses") Collection<String> openStatuses,
      @Param("now") Instant now);

  // Open tickets that have used up the warning share of their time but are not yet late.
  @Query("""
      select t from Ticket t
      where t.overdue = false and t.slaWarnedAt is null and t.status in :openStatuses
        and t.resolutionDueAt > :now
      order by t.resolutionDueAt
      """)
  List<Ticket> findWarningCandidates(@Param("openStatuses") Collection<String> openStatuses,
      @Param("now") Instant now);

  // Technicians seen on tickets with how many tickets each is working on right now.
  @Query("""
      select t.assigneeId as userId, max(t.assigneeName) as name,
             sum(case when t.status in :openStatuses then 1 else 0 end) as openTickets
      from Ticket t where t.assigneeId is not null
      group by t.assigneeId
      order by max(t.assigneeName)
      """)
  List<TechnicianLoad> technicianLoad(@Param("openStatuses") Collection<String> openStatuses);

  interface TechnicianLoad {
    String getUserId();

    String getName();

    Long getOpenTickets();
  }
}
