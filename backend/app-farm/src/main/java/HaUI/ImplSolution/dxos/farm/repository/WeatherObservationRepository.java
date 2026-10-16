// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.repository;

import HaUI.ImplSolution.dxos.farm.model.WeatherObservation;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, Long> {

  boolean existsByLatitudeAndLongitudeAndObservedAt(BigDecimal latitude, BigDecimal longitude,
      Instant observedAt);
}
