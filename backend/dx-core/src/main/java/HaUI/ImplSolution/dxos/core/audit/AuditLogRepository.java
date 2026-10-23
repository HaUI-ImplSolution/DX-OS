// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.audit;

import HaUI.ImplSolution.dxos.core.api.audit.AuditOutcome;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository
    extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {

  // Client addresses with at least minCount records of the given outcome since a point in time.
  @Query("select e.ip as ip, count(e) as total from AuditLogEntry e where e.outcome = :outcome"
      + " and e.occurredAt >= :since and e.ip is not null group by e.ip having count(e) >= :minCount")
  List<DeniedSource> countBySource(@Param("outcome") AuditOutcome outcome,
      @Param("since") Instant since, @Param("minCount") long minCount);

  interface DeniedSource {

    String getIp();

    long getTotal();
  }
}
