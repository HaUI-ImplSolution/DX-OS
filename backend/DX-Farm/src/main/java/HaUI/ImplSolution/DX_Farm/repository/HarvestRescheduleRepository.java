package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.HarvestReschedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HarvestRescheduleRepository extends JpaRepository<HarvestReschedule, Long> {

  List<HarvestReschedule> findByCropSeasonIdOrderByIdAsc(Long cropSeasonId);
}
