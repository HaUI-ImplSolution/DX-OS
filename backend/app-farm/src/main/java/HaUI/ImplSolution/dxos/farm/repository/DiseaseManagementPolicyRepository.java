// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.DiseaseManagementPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiseaseManagementPolicyRepository
    extends JpaRepository<DiseaseManagementPolicy, Long> {

  Optional<DiseaseManagementPolicy> findByDisease_Code(String diseaseCode);
}
