package HaUI.ImplSolution.DX_Farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.DX_Farm.model.Disease;
import HaUI.ImplSolution.DX_Farm.model.Pesticide;
import HaUI.ImplSolution.DX_Farm.repository.DiseaseRepository;
import HaUI.ImplSolution.DX_Farm.repository.PesticideRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogActiveFilterTest {

  @Mock
  private DiseaseRepository diseaseRepository;

  @Mock
  private PesticideRepository pesticideRepository;

  @Test
  void diseaseCatalogReadsOnlyActiveRows() {
    Disease disease = Disease.builder().id(1L).code("Rice___Blast").name("Bệnh đạo ôn trên lúa")
        .active(true).build();
    when(diseaseRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(disease));

    var result = new DiseaseService(diseaseRepository).listAll();

    assertThat(result).extracting(item -> item.code()).containsExactly("Rice___Blast");
    verify(diseaseRepository).findAllByActiveTrueOrderByIdAsc();
  }

  @Test
  void pesticideCatalogReadsOnlyActiveRows() {
    Pesticide pesticide = Pesticide.builder().id(1L).code("APPROVED_PRODUCT")
        .name("Approved product").unit("L/ha").active(true).build();
    when(pesticideRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(pesticide));

    var result = new PesticideService(pesticideRepository).listAll();

    assertThat(result).extracting(item -> item.code()).containsExactly("APPROVED_PRODUCT");
    verify(pesticideRepository).findAllByActiveTrueOrderByIdAsc();
  }
}
