package HaUI.ImplSolution.DX_Farm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Tạo role Postgres chỉ-đọc (superset_ro) cho BI dashboard (Superset), idempotent. Dùng
// JdbcTemplate thuần vì data.sql tách câu lệnh theo dấu `;`, không tương thích với khối
// PL/pgSQL `DO $$ ... $$;`.
@Component
@Order(1)
public class SupersetReadOnlyRoleSeeder implements CommandLineRunner {

  private final JdbcTemplate jdbcTemplate;
  private final String readOnlyPassword;

  public SupersetReadOnlyRoleSeeder(JdbcTemplate jdbcTemplate,
      @Value("${superset.readonly.password}") String readOnlyPassword) {
    this.jdbcTemplate = jdbcTemplate;
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
    jdbcTemplate.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO superset_ro");
    jdbcTemplate
        .execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO superset_ro");
  }
}
