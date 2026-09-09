package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.PesticideResponse;
import HaUI.ImplSolution.DX_Farm.model.Pesticide;
import HaUI.ImplSolution.DX_Farm.repository.PesticideRepository;
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
    return pesticideRepository.findAll().stream().map(PesticideService::toResponse).toList();
  }

  public static PesticideResponse toResponse(Pesticide pesticide) {
    return new PesticideResponse(pesticide.getId(), pesticide.getCode(), pesticide.getName(),
        pesticide.getUnit());
  }
}
