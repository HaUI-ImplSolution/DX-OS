// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.audit;

import HaUI.ImplSolution.dxos.core.api.audit.AuditOutcome;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogQueryService {

  private static final int MAX_PAGE_SIZE = 200;

  private final AuditLogRepository repository;

  public AuditLogQueryService(AuditLogRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditLogResponse> search(String actor, String action, AuditOutcome outcome,
      Instant from, Instant to, int page, int size) {
    Specification<AuditLogEntry> filter = (root, query, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (actor != null && !actor.isBlank()) {
        predicates.add(builder.or(builder.equal(root.get("actorUserId"), actor.strip()),
            builder.equal(builder.lower(root.get("actorName")), actor.strip().toLowerCase())));
      }
      if (action != null && !action.isBlank()) {
        predicates.add(builder.equal(root.get("action"), action.strip().toUpperCase()));
      }
      if (outcome != null) {
        predicates.add(builder.equal(root.get("outcome"), outcome));
      }
      if (from != null) {
        predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
      }
      if (to != null) {
        predicates.add(builder.lessThan(root.get("occurredAt"), to));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
    PageRequest pageRequest =
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
    return PageResponse.of(repository.findAll(filter, pageRequest), AuditLogResponse::of);
  }
}
