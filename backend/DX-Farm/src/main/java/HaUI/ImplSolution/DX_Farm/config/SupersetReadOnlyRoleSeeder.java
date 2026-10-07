package HaUI.ImplSolution.DX_Farm.config;

import java.util.List;
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

  // Views hold approved or settled records only (see V9__bi_views.sql); catalogues are public data.
  private static final List<String> READABLE_RELATIONS =
      List.of("vw_parcels_approved", "vw_crop_seasons_approved", "vw_disease_reports_processed",
          "vw_diagnoses_approved", "vw_treatment_applications_confirmed",
          "vw_ai_diagnosis_outcomes", "diseases", "pesticides", "rice_varieties");

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final String readOnlyPassword;

  public SupersetReadOnlyRoleSeeder(JdbcTemplate jdbcTemplate,
      PlatformTransactionManager transactionManager,
      @Value("${superset.readonly.password}") String readOnlyPassword) {
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
    // The role reads an allowlist only: the BI views and the reference catalogues. Anything else,
    // including tables added later, stays closed until it is listed here.
    jdbcTemplate
        .execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM superset_ro");
    transactionTemplate.executeWithoutResult(status -> {
      jdbcTemplate.execute("REVOKE ALL ON ALL TABLES IN SCHEMA public FROM superset_ro");
      jdbcTemplate
          .execute("GRANT SELECT ON " + String.join(", ", READABLE_RELATIONS) + " TO superset_ro");
    });
  }
}
