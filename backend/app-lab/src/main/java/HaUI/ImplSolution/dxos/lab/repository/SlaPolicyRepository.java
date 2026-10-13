// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.repository;

import HaUI.ImplSolution.dxos.lab.model.SlaPolicy;
import HaUI.ImplSolution.dxos.lab.model.TicketPriority;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

  Optional<SlaPolicy> findByPriority(TicketPriority priority);
}
