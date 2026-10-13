// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.farm.dto.DiseaseResponse;
import HaUI.ImplSolution.dxos.farm.model.Disease;
import HaUI.ImplSolution.dxos.farm.repository.DiseaseRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiseaseService {

  private final DiseaseRepository diseaseRepository;

  public DiseaseService(DiseaseRepository diseaseRepository) {
    this.diseaseRepository = diseaseRepository;
  }

  public List<DiseaseResponse> listAll() {
    return diseaseRepository.findAllByActiveTrueOrderByIdAsc().stream()
        .map(DiseaseService::toResponse).toList();
  }

  public static DiseaseResponse toResponse(Disease disease) {
    return new DiseaseResponse(disease.getId(), disease.getCode(), disease.getName(),
        disease.getDescription());
  }
}
