package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropSeasonRepository extends JpaRepository<CropSeason, Long> {

  List<CropSeason> findByParcel_Code(String parcelCode);
}
