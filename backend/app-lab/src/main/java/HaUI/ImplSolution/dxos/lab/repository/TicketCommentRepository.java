// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.repository;

import HaUI.ImplSolution.dxos.lab.model.TicketComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

  List<TicketComment> findByTicket_IdOrderByCreatedAtAscIdAsc(Long ticketId);

  List<TicketComment> findByTicket_IdAndInternalFalseOrderByCreatedAtAscIdAsc(Long ticketId);
}
