package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.CropSeasonStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropSeasonRepository extends JpaRepository<CropSeason, Long> {

  List<CropSeason> findByParcel_Code(String parcelCode);

  boolean existsByParcel_IdAndStatus(Long parcelId, CropSeasonStatus status);
}
