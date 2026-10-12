// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.Pesticide;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PesticideRepository extends JpaRepository<Pesticide, Long> {

  List<Pesticide> findAllByActiveTrueOrderByIdAsc();
}
