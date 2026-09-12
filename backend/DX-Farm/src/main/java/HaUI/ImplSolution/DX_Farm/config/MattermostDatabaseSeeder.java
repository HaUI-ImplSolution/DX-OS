package HaUI.ImplSolution.DX_Farm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Tạo role + database Postgres riêng, tách biệt khỏi CSDL nghiệp vụ, cho kênh chat nội bộ
// (Mattermost) lúc khởi động, idempotent. CREATE DATABASE không chạy được trong khối PL/pgSQL
// DO $$ ... $$ hay trong transaction, nên kiểm tra tồn tại trước rồi chạy như một câu lệnh JDBC
// trần, tách riêng khỏi phần tạo role.
@Component
@Order(2)
public class MattermostDatabaseSeeder implements CommandLineRunner {

  private static final String ROLE_NAME = "mattermost_app";
  private static final String DATABASE_NAME = "mattermost";

  private final JdbcTemplate jdbcTemplate;
  private final String appPassword;

  public MattermostDatabaseSeeder(JdbcTemplate jdbcTemplate,
      @Value("${mattermost.db.password}") String appPassword) {
    this.jdbcTemplate = jdbcTemplate;
    this.appPassword = appPassword;
  }

  @Override
  public void run(String... args) {
    String escapedPassword = appPassword.replace("'", "''");
    String roleSql = """
        DO $$
        BEGIN
          IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '%s') THEN
            CREATE ROLE %s LOGIN PASSWORD '%s';
          END IF;
        END
        $$;
        """.formatted(ROLE_NAME, ROLE_NAME, escapedPassword);
    jdbcTemplate.execute(roleSql);

    Boolean databaseExists = jdbcTemplate.queryForObject(
        "SELECT EXISTS (SELECT FROM pg_catalog.pg_database WHERE datname = ?)", Boolean.class,
        DATABASE_NAME);
    if (Boolean.FALSE.equals(databaseExists)) {
      jdbcTemplate.execute("CREATE DATABASE %s OWNER %s".formatted(DATABASE_NAME, ROLE_NAME));
    }
  }
}
