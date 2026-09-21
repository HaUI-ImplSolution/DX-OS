// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.farm.dto.PesticideResponse;
import HaUI.ImplSolution.dxos.farm.model.Pesticide;
import HaUI.ImplSolution.dxos.farm.repository.PesticideRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PesticideService {

  private final PesticideRepository pesticideRepository;

  public PesticideService(PesticideRepository pesticideRepository) {
    this.pesticideRepository = pesticideRepository;
  }

  public List<PesticideResponse> listAll() {
    return pesticideRepository.findAllByActiveTrueOrderByIdAsc().stream()
        .map(PesticideService::toResponse).toList();
  }

  public static PesticideResponse toResponse(Pesticide pesticide) {
    return new PesticideResponse(pesticide.getId(), pesticide.getCode(), pesticide.getName(),
        pesticide.getUnit());
  }
}
