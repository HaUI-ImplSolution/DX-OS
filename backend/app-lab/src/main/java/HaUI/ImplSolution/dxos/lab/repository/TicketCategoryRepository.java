// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.repository;

import HaUI.ImplSolution.dxos.lab.model.TicketCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

  Optional<TicketCategory> findByCode(String code);

  Optional<TicketCategory> findByCodeAndActiveTrue(String code);

  boolean existsByCode(String code);

  List<TicketCategory> findAllByOrderByNameAsc();

  List<TicketCategory> findByActiveTrueOrderByNameAsc();
}
