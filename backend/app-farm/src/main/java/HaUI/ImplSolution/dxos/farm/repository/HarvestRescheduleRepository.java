// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.HarvestReschedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HarvestRescheduleRepository extends JpaRepository<HarvestReschedule, Long> {

  List<HarvestReschedule> findByCropSeasonIdOrderByIdAsc(Long cropSeasonId);
}
