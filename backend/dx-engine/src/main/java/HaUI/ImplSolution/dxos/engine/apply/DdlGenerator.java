// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import static HaUI.ImplSolution.dxos.engine.apply.Sql.ident;
import static HaUI.ImplSolution.dxos.engine.apply.Sql.literal;
import static HaUI.ImplSolution.dxos.engine.apply.Sql.table;

import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// The SQL a model needs. Every statement can run again without effect.
public final class DdlGenerator {

  public static final String REPORT_ROLE = "report_ro";
  public static final List<String> STATUSES = List.of("submitted", "approved", "rejected");

  private DdlGenerator() {}

  public static String createSchema(Model model) {
    return "CREATE SCHEMA IF NOT EXISTS " + ident(model.schema());
  }

  public static String createTable(Model model, Entity entity) {
    List<String> columns = new ArrayList<>();
    columns.add("id BIGSERIAL PRIMARY KEY");
    columns.add("created_by TEXT NOT NULL");
    columns.add("created_at TIMESTAMPTZ NOT NULL DEFAULT now()");
    columns.add("updated_at TIMESTAMPTZ NOT NULL DEFAULT now()");
    columns.add("version BIGINT NOT NULL DEFAULT 0");
    if (entity.process() != null) {
      String values = STATUSES.stream().map(Sql::literal).collect(Collectors.joining(", "));
      columns.add("status TEXT NOT NULL DEFAULT 'submitted' CHECK (status IN (" + values + "))");
    }
    for (Field field : entity.fields().values()) {
      StringBuilder column =
          new StringBuilder(ident(field.name())).append(' ').append(sqlType(field.type()));
      if (field.required()) {
        column.append(" NOT NULL");
      }
      if (field.unique()) {
        column.append(" CONSTRAINT ").append(ident("uq_" + entity.name() + "_" + field.name()))
            .append(" UNIQUE");
      }
      if (field.type() == FieldType.CHOICE) {
        String values =
            field.options().stream().map(Sql::literal).collect(Collectors.joining(", "));
        column.append(" CONSTRAINT ").append(ident("ck_" + entity.name() + "_" + field.name()))
            .append(" CHECK (").append(ident(field.name())).append(" IN (").append(values)
            .append("))");
      }
      columns.add(column.toString());
    }
    return "CREATE TABLE IF NOT EXISTS " + table(model, entity) + " (" + String.join(", ", columns)
        + ")";
  }

  // Foreign keys and indexes come after every table exists, so entities may refer to each other.
  public static List<String> links(Model model, Entity entity) {
    List<String> statements = new ArrayList<>();
    statements.add("CREATE INDEX IF NOT EXISTS " + ident("ix_" + entity.name() + "_created_by")
        + " ON " + table(model, entity) + " (created_by)");
    for (Field field : entity.fields().values()) {
      if (field.type() != FieldType.REFERENCE) {
        continue;
      }
      Entity target = model.entities().get(field.to());
      String constraint = "fk_" + entity.name() + "_" + field.name();
      statements.add("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = "
          + literal(constraint) + " AND conrelid = " + literal(table(model, entity))
          + "::regclass) THEN ALTER TABLE " + table(model, entity) + " ADD CONSTRAINT "
          + ident(constraint) + " FOREIGN KEY (" + ident(field.name()) + ") REFERENCES "
          + table(model, target) + " (id); END IF; END $$");
      statements
          .add("CREATE INDEX IF NOT EXISTS " + ident("ix_" + entity.name() + "_" + field.name())
              + " ON " + table(model, entity) + " (" + ident(field.name()) + ")");
    }
    return statements;
  }

  // A role that can read the tables of the model and write nothing, for reports.
  public static List<String> readOnlyAccess(Model model, String password) {
    String create =
        password == null || password.isBlank() ? "NOLOGIN" : "LOGIN PASSWORD " + literal(password);
    return List.of(
        "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = " + literal(REPORT_ROLE)
            + ") THEN CREATE ROLE " + ident(REPORT_ROLE) + " " + create + "; END IF; END $$",
        "GRANT USAGE ON SCHEMA " + ident(model.schema()) + " TO " + ident(REPORT_ROLE),
        "GRANT SELECT ON ALL TABLES IN SCHEMA " + ident(model.schema()) + " TO "
            + ident(REPORT_ROLE));
  }

  private static String sqlType(FieldType type) {
    return switch (type) {
      case TEXT, CHOICE, USER -> "TEXT";
      case NUMBER -> "NUMERIC";
      case DATETIME -> "TIMESTAMPTZ";
      case REFERENCE -> "BIGINT";
    };
  }
}
