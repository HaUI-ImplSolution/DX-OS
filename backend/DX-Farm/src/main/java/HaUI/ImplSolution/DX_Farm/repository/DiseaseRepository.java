// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.Disease;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiseaseRepository extends JpaRepository<Disease, Long> {

  List<Disease> findAllByActiveTrueOrderByIdAsc();

  Optional<Disease> findByCodeAndActiveTrue(String code);
}
