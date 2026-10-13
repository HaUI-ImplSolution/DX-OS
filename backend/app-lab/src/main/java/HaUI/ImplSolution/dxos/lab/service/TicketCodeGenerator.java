// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Builds TK-<year>-<number>, e.g. TK-2026-0042. The counter row of the year is locked by the
// upsert, so two requests at once get different numbers.
@Component
public class TicketCodeGenerator {

  private static final String NEXT_NUMBER = """
      INSERT INTO ticket_code_counter (ticket_year, last_number) VALUES (?, 1)
      ON CONFLICT (ticket_year)
      DO UPDATE SET last_number = ticket_code_counter.last_number + 1
      RETURNING last_number
      """;

  private final JdbcTemplate jdbcTemplate;

  public TicketCodeGenerator(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // Must run inside the transaction that saves the ticket, so a failed save gives the number back.
  public String next(int year) {
    Integer number = jdbcTemplate.queryForObject(NEXT_NUMBER, Integer.class, year);
    return "TK-%d-%04d".formatted(year, number);
  }
}
