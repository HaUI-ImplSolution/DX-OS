package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.CropSeason;
import HaUI.ImplSolution.DX_Farm.model.CropSeasonStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropSeasonRepository extends JpaRepository<CropSeason, Long> {

  List<CropSeason> findByParcel_CodeAndDeletedAtIsNullOrderByIdAsc(String parcelCode);

  List<CropSeason> findByParcel_CodeOrderByIdAsc(String parcelCode);

  boolean existsByParcel_IdAndStatusAndDeletedAtIsNull(Long parcelId, CropSeasonStatus status);

  boolean existsByParcel_IdAndDeletedAtIsNull(Long parcelId);
}
