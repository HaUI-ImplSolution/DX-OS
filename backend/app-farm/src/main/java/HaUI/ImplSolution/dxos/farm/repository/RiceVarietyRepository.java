// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.RiceVariety;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiceVarietyRepository extends JpaRepository<RiceVariety, Long> {

  List<RiceVariety> findByActiveTrueOrderByNameAsc();

  Optional<RiceVariety> findByCodeAndActiveTrue(String code);
}
