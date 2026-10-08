// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.DiseaseManagementPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiseaseManagementPolicyRepository
    extends JpaRepository<DiseaseManagementPolicy, Long> {

  Optional<DiseaseManagementPolicy> findByDisease_Code(String diseaseCode);
}
