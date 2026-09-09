package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.DiseaseResponse;
import HaUI.ImplSolution.DX_Farm.model.Disease;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseRepository;
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
    return diseaseRepository.findAll().stream().map(DiseaseService::toResponse).toList();
  }

  public static DiseaseResponse toResponse(Disease disease) {
    return new DiseaseResponse(disease.getId(), disease.getCode(), disease.getName(),
        disease.getDescription());
  }
}
