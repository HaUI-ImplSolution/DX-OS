// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.config;

import HaUI.ImplSolution.dxos.core.api.bi.BiDatasetProvider;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Tạo role Postgres chỉ-đọc (superset_ro) cho BI dashboard (Superset), idempotent. Dùng
// JdbcTemplate thuần vì data.sql tách câu lệnh theo dấu `;`, không tương thích với khối
// PL/pgSQL `DO $$ ... $$;`.
@Component
@Order(1)
public class SupersetReadOnlyRoleSeeder implements CommandLineRunner {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final String readOnlyPassword;
  private final List<String> readableRelations;

  public SupersetReadOnlyRoleSeeder(JdbcTemplate jdbcTemplate,
      PlatformTransactionManager transactionManager,
      ObjectProvider<BiDatasetProvider> datasetProviders,
      @Value("${superset.readonly.password}") String readOnlyPassword) {
    this.readableRelations = datasetProviders.orderedStream()
        .flatMap(provider -> provider.relations().stream()).distinct().toList();
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.readOnlyPassword = readOnlyPassword;
  }

  @Override
  public void run(String... args) {
    // Nối chuỗi tay vì JDBC không bind tham số được vào thân khối DO $$ ... $$; password đến
    // từ biến môi trường triển khai nên escape dấu nháy đơn là đủ.
    String escapedPassword = readOnlyPassword.replace("'", "''");
    String sql = """
        DO $$
        BEGIN
          IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'superset_ro') THEN
            CREATE ROLE superset_ro LOGIN PASSWORD '%s';
          END IF;
          EXECUTE format('GRANT CONNECT ON DATABASE %%I TO superset_ro', current_database());
        END
        $$;
        """.formatted(escapedPassword);
    jdbcTemplate.execute(sql);
    jdbcTemplate.execute("GRANT USAGE ON SCHEMA public TO superset_ro");
    // The role reads an allowlist only: the relations the app registered. Anything else, including
    // tables added later, stays closed until it is registered.
    jdbcTemplate
        .execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM superset_ro");
    transactionTemplate.executeWithoutResult(status -> {
      jdbcTemplate.execute("REVOKE ALL ON ALL TABLES IN SCHEMA public FROM superset_ro");
      if (!readableRelations.isEmpty()) {
        jdbcTemplate
            .execute("GRANT SELECT ON " + String.join(", ", readableRelations) + " TO superset_ro");
      }
    });
  }
}
