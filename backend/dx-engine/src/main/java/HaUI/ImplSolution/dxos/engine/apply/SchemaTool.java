// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import HaUI.ImplSolution.dxos.model.Model;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Runs pgschema, which compares a schema of the database with a desired state and changes it. A
// plan file is applied exactly as it was checked; pgschema refuses it if the schema changed since.
@Component
public class SchemaTool {

  public record Statement(String sql, String type, String operation, String path) {

    // Whether it removes stored data: a table or a column, not a constraint or a privilege.
    public boolean dropsData() {
      return "drop".equals(operation) && ("table".equals(type) || "table.column".equals(type));
    }
  }

  public record Plan(String json, List<Statement> statements) {}

  private static final long TIMEOUT_SECONDS = 120;
  private static final int MESSAGE_LENGTH = 500;

  private final ObjectMapper json;
  private final String command;
  private final String host;
  private final String port;
  private final String database;
  private final String user;
  private final String password;

  public SchemaTool(
      ObjectMapper json,
      @Value("${engine.pgschema-command:pgschema}") String command,
      @Value("${POSTGRES_HOST}") String host,
      @Value("${POSTGRES_PORT}") String port,
      @Value("${POSTGRES_DB}") String database,
      @Value("${POSTGRES_USER}") String user,
      @Value("${POSTGRES_PASSWORD}") String password) {
    this.json = json;
    this.command = command;
    this.host = host;
    this.port = port;
    this.database = database;
    this.user = user;
    this.password = password;
  }

  public Plan plan(Model model, String desiredState) {
    Path desired = null;
    Path output = null;
    try {
      desired = Files.createTempFile("dxos-desired", ".sql");
      output = Files.createTempFile("dxos-plan", ".json");
      Files.writeString(desired, desiredState, StandardCharsets.UTF_8);
      run(model, "plan", "--file", desired.toString(), "--output-json", output.toString());
      String text = Files.readString(output, StandardCharsets.UTF_8);
      return new Plan(text, statements(text));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot use a temporary file: " + e.getMessage(), e);
    } finally {
      delete(desired);
      delete(output);
    }
  }

  public void apply(Model model, Plan plan) {
    if (plan.statements().isEmpty()) {
      return;
    }
    Path file = null;
    try {
      file = Files.createTempFile("dxos-plan", ".json");
      Files.writeString(file, plan.json(), StandardCharsets.UTF_8);
      run(model, "apply", "--plan", file.toString(), "--auto-approve");
    } catch (IOException e) {
      throw new IllegalStateException("Cannot use a temporary file: " + e.getMessage(), e);
    } finally {
      delete(file);
    }
  }

  private List<Statement> statements(String text) {
    List<Statement> statements = new ArrayList<>();
    for (JsonNode group : json.readTree(text).path("groups")) {
      for (JsonNode step : group.path("steps")) {
        statements.add(
            new Statement(
                step.path("sql").asString(),
                step.path("type").asString(),
                step.path("operation").asString(),
                step.path("path").asString()));
      }
    }
    return statements;
  }

  private void run(Model model, String action, String... options) throws IOException {
    List<String> arguments =
        new ArrayList<>(
            List.of(
                command,
                action,
                "--host",
                host,
                "--port",
                port,
                "--db",
                database,
                "--user",
                user,
                "--schema",
                model.schema(),
                "--no-color"));
    arguments.addAll(List.of(options));
    ProcessBuilder builder = new ProcessBuilder(arguments).redirectErrorStream(true);
    Map<String, String> environment = builder.environment();
    environment.put("PGPASSWORD", password);
    // The tool tries a desired state in a temporary schema of this same database.
    environment.put("PGSCHEMA_PLAN_HOST", host);
    environment.put("PGSCHEMA_PLAN_PORT", port);
    environment.put("PGSCHEMA_PLAN_DB", database);
    environment.put("PGSCHEMA_PLAN_USER", user);
    environment.put("PGSCHEMA_PLAN_PASSWORD", password);
    Path log = Files.createTempFile("dxos-schema-tool", ".log");
    try {
      Process process = builder.redirectOutput(log.toFile()).start();
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IllegalStateException("The schema tool did not finish in time");
      }
      if (process.exitValue() != 0) {
        String output = Files.readString(log, StandardCharsets.UTF_8).strip();
        int start = Math.max(0, output.length() - MESSAGE_LENGTH);
        throw new IllegalStateException("The schema tool failed: " + output.substring(start));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while running the schema tool", e);
    } finally {
      delete(log);
    }
  }

  private static void delete(Path path) {
    if (path != null) {
      try {
        Files.deleteIfExists(path);
      } catch (IOException ignored) {
        // A leftover temporary file does no harm.
      }
    }
  }
}
