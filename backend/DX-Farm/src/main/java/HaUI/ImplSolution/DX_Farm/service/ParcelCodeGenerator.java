// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.identity.Household;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Builds <HTX>-<household>-T<number>, e.g. HTX01-H0042-T03. The counter row of the household is
// locked by the upsert, so two requests at once get different numbers.
@Component
public class ParcelCodeGenerator {

  private static final String NEXT_NUMBER = """
      INSERT INTO household_parcel_counter (household_id, last_number) VALUES (?, 1)
      ON CONFLICT (household_id)
      DO UPDATE SET last_number = household_parcel_counter.last_number + 1
      RETURNING last_number
      """;

  private final JdbcTemplate jdbcTemplate;
  private final String htxCode;

  public ParcelCodeGenerator(JdbcTemplate jdbcTemplate,
      @Value("${app.htx-code:HTX01}") String htxCode) {
    this.jdbcTemplate = jdbcTemplate;
    this.htxCode = htxCode;
  }

  // Must run inside the transaction that saves the parcel, so a failed save gives the number back.
  public String next(Household household) {
    Integer number = jdbcTemplate.queryForObject(NEXT_NUMBER, Integer.class, household.getId());
    return "%s-%s-T%02d".formatted(htxCode, household.getCode(), number);
  }
}
