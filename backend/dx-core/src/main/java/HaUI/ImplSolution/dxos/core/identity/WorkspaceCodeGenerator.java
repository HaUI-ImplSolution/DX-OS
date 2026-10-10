// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.identity;

import HaUI.ImplSolution.dxos.core.api.workspace.WorkspaceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Builds <prefix><number> (e.g. H0042) from one counter row per workspace type. The upsert locks
// the row, so two requests at once get different numbers.
@Component
public class WorkspaceCodeGenerator {

  private static final String NEXT_NUMBER = """
      INSERT INTO workspace_code_counter (type, last_number) VALUES (?, 1)
      ON CONFLICT (type)
      DO UPDATE SET last_number = workspace_code_counter.last_number + 1
      RETURNING last_number
      """;

  private final JdbcTemplate jdbcTemplate;

  public WorkspaceCodeGenerator(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // Must run inside the transaction that saves the workspace, so a failed save gives the number
  // back.
  public String next(WorkspaceType type) {
    Long number = jdbcTemplate.queryForObject(NEXT_NUMBER, Long.class, type.type());
    return "%s%04d".formatted(type.codePrefix(), number);
  }
}
