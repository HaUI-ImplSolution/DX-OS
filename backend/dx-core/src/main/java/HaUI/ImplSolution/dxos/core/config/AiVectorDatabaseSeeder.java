// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Tạo database pgvector riêng (dx_farm_ai_vector_db) cho AI Vector DB lúc khởi động, idempotent.
// Khác Keycloak/Mattermost (tự tạo schema của chính chúng), AI không dùng migration (xem
// ai/README.md) nên Backend phải tự tạo luôn extension pgvector và 2 bảng
// documents/document_chunks trong database mới này, không chỉ tạo database rỗng.
// Cũng tạo 2 role riêng cho AI: ai_vector_ro (chỉ-đọc, dùng cho runtime suy luận) và
// ai_vector_ingest (đọc/ghi, dùng cho job ingest RAG) — cả hai đều bị revoke CONNECT khỏi DB
// nghiệp vụ của Backend, không role nào của AI được phép chạm vào Backend DB kể cả read-only.
@Component
@Order(4)
public class AiVectorDatabaseSeeder implements CommandLineRunner {

  private static final String DATABASE_NAME = "dx_farm_ai_vector_db";
  private static final String READONLY_ROLE = "ai_vector_ro";
  private static final String INGEST_ROLE = "ai_vector_ingest";

  private final JdbcTemplate jdbcTemplate;
  private final String host;
  private final String port;
  private final String adminUser;
  private final String adminPassword;
  private final String backendDatabaseName;
  private final String readonlyPassword;
  private final String ingestPassword;

  public AiVectorDatabaseSeeder(JdbcTemplate jdbcTemplate, @Value("${POSTGRES_HOST}") String host,
      @Value("${POSTGRES_PORT}") String port, @Value("${POSTGRES_USER}") String adminUser,
      @Value("${POSTGRES_PASSWORD}") String adminPassword,
      @Value("${POSTGRES_DB}") String backendDatabaseName,
      @Value("${ai.vector-db.readonly.password}") String readonlyPassword,
      @Value("${ai.vector-db.ingest.password}") String ingestPassword) {
    this.jdbcTemplate = jdbcTemplate;
    this.host = host;
    this.port = port;
    this.adminUser = adminUser;
    this.adminPassword = adminPassword;
    this.backendDatabaseName = backendDatabaseName;
    this.readonlyPassword = readonlyPassword;
    this.ingestPassword = ingestPassword;
  }

  @Override
  public void run(String... args) throws SQLException {
    Boolean databaseExists = jdbcTemplate.queryForObject(
        "SELECT EXISTS (SELECT FROM pg_catalog.pg_database WHERE datname = ?)", Boolean.class,
        DATABASE_NAME);
    if (Boolean.FALSE.equals(databaseExists)) {
      jdbcTemplate.execute("CREATE DATABASE %s".formatted(DATABASE_NAME));
    }

    createRole(READONLY_ROLE, readonlyPassword);
    createRole(INGEST_ROLE, ingestPassword);

    // PostgreSQL cấp CONNECT cho PUBLIC theo mặc định. Revoke trực tiếp từng role không thể
    // ghi đè quyền kế thừa này, nên phải đóng PUBLIC; owner Backend và superset_ro vẫn có
    // quyền CONNECT tường minh từ quyền sở hữu/seeder riêng.
    jdbcTemplate
        .execute("REVOKE CONNECT ON DATABASE %s FROM PUBLIC".formatted(backendDatabaseName));

    String url = "jdbc:postgresql://%s:%s/%s".formatted(host, port, DATABASE_NAME);
    try (Connection connection = DriverManager.getConnection(url, adminUser, adminPassword);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE EXTENSION IF NOT EXISTS vector");

      statement.execute("""
          CREATE TABLE IF NOT EXISTS documents (
            id UUID PRIMARY KEY,
            title VARCHAR(255) NOT NULL,
            source VARCHAR(500),
            document_type VARCHAR(100),
            version VARCHAR(50),
            disease_code VARCHAR(100),
            created_at TIMESTAMPTZ NOT NULL
          )
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS ix_documents_disease_code ON documents (disease_code)");

      statement.execute("""
          CREATE TABLE IF NOT EXISTS document_chunks (
            id UUID PRIMARY KEY,
            document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
            chunk_index INTEGER NOT NULL,
            content TEXT NOT NULL,
            page_number INTEGER,
            embedding VECTOR(384)
          )
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS ix_document_chunks_document_id ON document_chunks (document_id)");

      statement.execute("GRANT CONNECT ON DATABASE %s TO %s, %s".formatted(DATABASE_NAME,
          READONLY_ROLE, INGEST_ROLE));
      statement
          .execute("GRANT USAGE ON SCHEMA public TO %s, %s".formatted(READONLY_ROLE, INGEST_ROLE));

      statement
          .execute("GRANT SELECT ON documents, document_chunks TO %s".formatted(READONLY_ROLE));
      statement.execute("""
          ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO %s
          """.formatted(READONLY_ROLE));

      statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON documents, document_chunks TO %s"
          .formatted(INGEST_ROLE));
      statement.execute(
          """
              ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %s
              """
              .formatted(INGEST_ROLE));
    }
  }

  private void createRole(String roleName, String password) {
    String escapedPassword = password.replace("'", "''");
    String sql = """
        DO $$
        BEGIN
          IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '%s') THEN
            CREATE ROLE %s LOGIN PASSWORD '%s';
          END IF;
        END
        $$;
        """.formatted(roleName, roleName, escapedPassword);
    jdbcTemplate.execute(sql);
  }
}
