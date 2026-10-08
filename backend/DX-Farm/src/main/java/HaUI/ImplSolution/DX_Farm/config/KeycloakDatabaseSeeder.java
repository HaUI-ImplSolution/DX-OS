// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Tạo role + database Postgres riêng cho Keycloak lúc khởi động, idempotent.
// CREATE DATABASE không chạy được trong khối PL/pgSQL DO $$, nên tách khỏi phần tạo role.
@Component
@Order(3)
public class KeycloakDatabaseSeeder implements CommandLineRunner {

  private static final String ROLE_NAME = "keycloak_app";
  private static final String DATABASE_NAME = "keycloak";

  private final JdbcTemplate jdbcTemplate;
  private final String appPassword;

  public KeycloakDatabaseSeeder(JdbcTemplate jdbcTemplate,
      @Value("${keycloak.db.password}") String appPassword) {
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
