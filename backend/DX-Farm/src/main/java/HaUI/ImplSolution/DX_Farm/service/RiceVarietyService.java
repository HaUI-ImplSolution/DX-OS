package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.dto.RiceVarietyResponse;
import HaUI.ImplSolution.DX_Farm.repository.RiceVarietyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RiceVarietyService {

  private final RiceVarietyRepository riceVarietyRepository;

  public RiceVarietyService(RiceVarietyRepository riceVarietyRepository) {
    this.riceVarietyRepository = riceVarietyRepository;
  }

  public List<RiceVarietyResponse> listActive() {
    return riceVarietyRepository.findByActiveTrueOrderByNameAsc().stream()
        .map(variety -> new RiceVarietyResponse(variety.getCode(), variety.getName(),
            variety.getGrowthDaysMin(), variety.getGrowthDaysMax(), variety.getSource(),
            variety.getReviewStatus()))
        .toList();
  }
}
