// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.ParcelEditRequest;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParcelEditRequestRepository extends JpaRepository<ParcelEditRequest, Long> {

  List<ParcelEditRequest> findByParcel_IdIn(Collection<Long> parcelIds);
}
