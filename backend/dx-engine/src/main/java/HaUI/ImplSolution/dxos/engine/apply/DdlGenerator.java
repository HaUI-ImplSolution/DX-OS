// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import static HaUI.ImplSolution.dxos.engine.apply.Sql.constraint;
import static HaUI.ImplSolution.dxos.engine.apply.Sql.ident;
import static HaUI.ImplSolution.dxos.engine.apply.Sql.literal;
import static HaUI.ImplSolution.dxos.engine.apply.Sql.table;

import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Field;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// The SQL a model needs.
public final class DdlGenerator {

  public static final String REPORT_ROLE = "report_ro";

  private DdlGenerator() {}

  public static String createSchema(Model model) {
    return "CREATE SCHEMA IF NOT EXISTS " + ident(model.schema());
  }

  // The tables, constraints, indexes and read access a model needs, for the schema tool to reach.
  // A renamed field keeps the column name given in names, which is how a preview sees it before
  // the rename has run.
  public static String desiredState(Model model, Map<String, Map<String, String>> names) {
    List<String> statements = new ArrayList<>();
    for (Entity entity : model.entities().values()) {
      statements.add(createTable(entity, names.getOrDefault(entity.name(), Map.of())));
    }
    for (Entity entity : model.entities().values()) {
      statements.addAll(links(entity, names.getOrDefault(entity.name(), Map.of())));
      statements.add("GRANT SELECT ON " + ident(entity.name()) + " TO " + ident(REPORT_ROLE));
    }
    return String.join(";\n", statements) + ";\n";
  }

  private static String createTable(Entity entity, Map<String, String> names) {
    List<String> columns = new ArrayList<>();
    columns.add("id BIGSERIAL PRIMARY KEY");
    columns.add("created_by TEXT NOT NULL");
    columns.add("created_at TIMESTAMPTZ NOT NULL DEFAULT now()");
    columns.add("updated_at TIMESTAMPTZ NOT NULL DEFAULT now()");
    columns.add("version BIGINT NOT NULL DEFAULT 0");
    if (entity.process() != null) {
      ProcessFamily template = entity.process().definition();
      String values = template.states().stream().map(state -> literal(state.name()))
          .collect(Collectors.joining(", "));
      columns.add("status TEXT NOT NULL DEFAULT " + literal(template.initial())
          + " CHECK (status IN (" + values + "))");
      columns.add("assignee TEXT");
      columns.add("decided_by TEXT");
      columns.add("decided_at TIMESTAMPTZ");
      columns.add("handle_by TIMESTAMPTZ");
    }
    for (Field field : entity.fields().values()) {
      String name = names.getOrDefault(field.name(), field.name());
      StringBuilder column = new StringBuilder(ident(name)).append(' ').append(sqlType(field));
      if (field.defaultValue() != null) {
        column.append(" DEFAULT ").append(field.type() == FieldType.NUMBER ? field.defaultValue()
            : literal(field.defaultValue()));
      }
      if (field.required()) {
        column.append(" NOT NULL");
      }
      if (field.unique()) {
        column.append(" CONSTRAINT ").append(ident("uq_" + entity.name() + "_" + name))
            .append(" UNIQUE");
      }
      if (field.type() == FieldType.CHOICE || field.type() == FieldType.UNIT) {
        String values =
            field.options().stream().map(Sql::literal).collect(Collectors.joining(", "));
        String test = field.multiple() ? ident(name) + " <@ ARRAY[" + values + "]::text[]"
            : ident(name) + " IN (" + values + ")";
        column.append(" CONSTRAINT ").append(ident("ck_" + entity.name() + "_" + name))
            .append(" CHECK (").append(test).append(")");
      }
      if (field.min() != null) {
        column.append(" CONSTRAINT ")
            .append(constraint("ck_" + entity.name() + "_" + name + "$min")).append(" CHECK (")
            .append(ident(name)).append(" >= ").append(bound(field, field.min())).append(")");
      }
      if (field.max() != null) {
        column.append(" CONSTRAINT ")
            .append(constraint("ck_" + entity.name() + "_" + name + "$max")).append(" CHECK (")
            .append(ident(name)).append(" <= ").append(bound(field, field.max())).append(")");
      }
      if (field.pattern() != null) {
        column.append(" CONSTRAINT ").append(constraint("ck_" + entity.name() + "_" + name + "$re"))
            .append(" CHECK (").append(ident(name)).append(" ~ ")
            .append(literal(anchored(field.pattern()))).append(")");
      }
      columns.add(column.toString());
    }
    return "CREATE TABLE " + ident(entity.name()) + " (" + String.join(", ", columns) + ")";
  }

  // Foreign keys and indexes come after every table exists, so entities may refer to each other.
  private static List<String> links(Entity entity, Map<String, String> names) {
    List<String> statements = new ArrayList<>();
    statements.add(index(entity, "created_by"));
    if (entity.process() != null) {
      statements.add(index(entity, "status"));
      statements.add(index(entity, "assignee"));
      statements.add(index(entity, "handle_by"));
    }
    entity.unitField().ifPresent(
        field -> statements.add(index(entity, names.getOrDefault(field.name(), field.name()))));
    for (Field field : entity.fields().values()) {
      if (field.type() != FieldType.REFERENCE) {
        continue;
      }
      String name = names.getOrDefault(field.name(), field.name());
      statements.add("ALTER TABLE " + ident(entity.name()) + " ADD CONSTRAINT "
          + ident("fk_" + entity.name() + "_" + name) + " FOREIGN KEY (" + ident(name)
          + ") REFERENCES " + ident(field.to()) + " (id)");
      statements.add(index(entity, name));
    }
    return statements;
  }

  private static String index(Entity entity, String column) {
    return "CREATE INDEX " + ident("ix_" + entity.name() + "_" + column) + " ON "
        + ident(entity.name()) + " (" + ident(column) + ")";
  }

  // Renames a column with the constraints and the index named after it, in one statement. It does
  // nothing when the rename has already run, so an interrupted apply can run it again.
  public static String renameField(Model model, Entity entity, String from, String to) {
    String schema = literal(model.schema());
    String table = literal(entity.name());
    String prefix = entity.name() + "_";
    StringBuilder body = new StringBuilder();
    body.append(
        "DO $$ BEGIN IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = ")
        .append(schema).append(" AND table_name = ").append(table).append(" AND column_name = ")
        .append(literal(from)).append(") THEN ALTER TABLE ").append(table(model, entity))
        .append(" RENAME COLUMN ").append(ident(from)).append(" TO ").append(ident(to))
        .append("; END IF; ");
    for (String kind : List.of("uq_", "ck_", "fk_")) {
      for (String suffix : kind.equals("ck_") ? List.of("", "$min", "$max", "$re") : List.of("")) {
        String oldName = kind + prefix + from + suffix;
        body.append("IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = ")
            .append(literal(oldName)).append(" AND conrelid = ")
            .append(literal(table(model, entity))).append("::regclass) THEN ALTER TABLE ")
            .append(table(model, entity)).append(" RENAME CONSTRAINT ").append(constraint(oldName))
            .append(" TO ").append(constraint(kind + prefix + to + suffix)).append("; END IF; ");
      }
    }
    body.append("ALTER INDEX IF EXISTS ").append(ident(model.schema())).append('.')
        .append(ident("ix_" + prefix + from)).append(" RENAME TO ")
        .append(ident("ix_" + prefix + to)).append("; END $$");
    return body.toString();
  }

  // The schema tool works inside a schema, so access to the schema itself is granted apart.
  public static String grantSchemaUsage(Model model) {
    return "GRANT USAGE ON SCHEMA " + ident(model.schema()) + " TO " + ident(REPORT_ROLE);
  }

  // A role that can read and write nothing by itself; the desired state grants it read access.
  public static String reportRole(String password) {
    String create =
        password == null || password.isBlank() ? "NOLOGIN" : "LOGIN PASSWORD " + literal(password);
    return "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = "
        + literal(REPORT_ROLE) + ") THEN CREATE ROLE " + ident(REPORT_ROLE) + " " + create
        + "; END IF; END $$";
  }

  // The column type of a field; a field with several values is an array of text.
  public static String sqlType(Field field) {
    return field.multiple() ? "TEXT[]" : sqlType(field.type());
  }

  // A bound as SQL: a number as written, a date as a typed literal. The model reader has already
  // checked the form of both.
  public static String bound(Field field, String value) {
    return field.type() == FieldType.DATE ? "DATE " + literal(value)
        : new java.math.BigDecimal(value).toPlainString();
  }

  // A pattern matches the whole value, which the SQL operator does not do by itself.
  public static String anchored(String pattern) {
    return "^(?:" + pattern + ")$";
  }

  public static String sqlType(FieldType type) {
    return switch (type) {
      case TEXT, CHOICE, USER, UNIT, FILE -> "TEXT";
      case NUMBER -> "NUMERIC";
      case DATETIME -> "TIMESTAMPTZ";
      case REFERENCE -> "BIGINT";
      case BOOLEAN -> "BOOLEAN";
      case DATE -> "DATE";
    };
  }
}
