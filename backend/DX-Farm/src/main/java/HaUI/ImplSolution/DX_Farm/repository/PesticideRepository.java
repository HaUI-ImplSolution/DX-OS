package HaUI.ImplSolution.DX_Farm.repository;

import HaUI.ImplSolution.DX_Farm.model.Pesticide;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PesticideRepository extends JpaRepository<Pesticide, Long> {

  List<Pesticide> findAllByActiveTrueOrderByIdAsc();
}
