package HaUI.ImplSolution.DX_Farm.config;

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
@Component
@Order(4)
public class AiVectorDatabaseSeeder implements CommandLineRunner {

  private static final String DATABASE_NAME = "dx_farm_ai_vector_db";

  private final JdbcTemplate jdbcTemplate;
  private final String host;
  private final String port;
  private final String adminUser;
  private final String adminPassword;

  public AiVectorDatabaseSeeder(JdbcTemplate jdbcTemplate, @Value("${POSTGRES_HOST}") String host,
      @Value("${POSTGRES_PORT}") String port, @Value("${POSTGRES_USER}") String adminUser,
      @Value("${POSTGRES_PASSWORD}") String adminPassword) {
    this.jdbcTemplate = jdbcTemplate;
    this.host = host;
    this.port = port;
    this.adminUser = adminUser;
    this.adminPassword = adminPassword;
  }

  @Override
  public void run(String... args) throws SQLException {
    Boolean databaseExists = jdbcTemplate.queryForObject(
        "SELECT EXISTS (SELECT FROM pg_catalog.pg_database WHERE datname = ?)", Boolean.class,
        DATABASE_NAME);
    if (Boolean.FALSE.equals(databaseExists)) {
      jdbcTemplate.execute("CREATE DATABASE %s".formatted(DATABASE_NAME));
    }

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
    }
  }
}
