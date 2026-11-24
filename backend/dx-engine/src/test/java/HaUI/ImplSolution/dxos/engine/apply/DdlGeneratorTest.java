// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DdlGeneratorTest {

  private static Model model(String fields, String rest) {
    return ModelParser.parse(
        "format: 1\nname: shop\nunits:\n  - name: LAB\nroles:\n  - name: A\nentities:\n  parent:\n"
            + "    fields:\n      t: {type: text}\n    access: {A: all}\n  item:\n    fields:\n"
            + fields
            + "    access: {A: all}\n"
            + rest);
  }

  private static String ddl(String fields, String rest) {
    return DdlGenerator.desiredState(model(fields, rest), Map.of());
  }

  private static String ddl(String fields) {
    return ddl(fields, "");
  }

  @Test
  void everyTableHasTheColumnsOfAnyRecord() {
    String ddl = ddl("      a: {type: text}\n");

    assertTrue(
        ddl.contains("CREATE TABLE \"item\" (id BIGSERIAL PRIMARY KEY, created_by TEXT NOT NULL"),
        ddl);
    assertTrue(ddl.contains("created_at TIMESTAMPTZ NOT NULL DEFAULT now()"));
    assertTrue(ddl.contains("version BIGINT NOT NULL DEFAULT 0"));
    assertTrue(ddl.contains("\"a\" TEXT"));
    assertFalse(ddl.contains("status TEXT"), "an entity without a process has no status");
  }

  @Test
  void aProcessAddsTheStatusAndTheColumnsOfItsHandling() {
    String ddl = ddl("      a: {type: text}\n", "    process: {family: approval, handler: A}\n");

    assertTrue(
        ddl.contains("status TEXT NOT NULL DEFAULT 'submitted' CHECK (status IN ('submitted'"),
        ddl);
    assertTrue(ddl.contains("'approved'"));
    assertTrue(
        ddl.contains(
            "assignee TEXT, decided_by TEXT, decided_at TIMESTAMPTZ, handle_by TIMESTAMPTZ"));
  }

  @Test
  void eachTypeHasItsColumnType() {
    assertEquals("TEXT", DdlGenerator.sqlType(FieldType.TEXT));
    assertEquals("TEXT", DdlGenerator.sqlType(FieldType.CHOICE));
    assertEquals("TEXT", DdlGenerator.sqlType(FieldType.USER));
    assertEquals("TEXT", DdlGenerator.sqlType(FieldType.UNIT));
    assertEquals("TEXT", DdlGenerator.sqlType(FieldType.FILE));
    assertEquals("TEXT", DdlGenerator.sqlType(FieldType.CODE));
    assertEquals("NUMERIC", DdlGenerator.sqlType(FieldType.NUMBER));
    assertEquals("TIMESTAMPTZ", DdlGenerator.sqlType(FieldType.DATETIME));
    assertEquals("BIGINT", DdlGenerator.sqlType(FieldType.REFERENCE));
    assertEquals("BOOLEAN", DdlGenerator.sqlType(FieldType.BOOLEAN));
    assertEquals("DATE", DdlGenerator.sqlType(FieldType.DATE));
  }

  @Test
  void aRequiredFieldIsNotNullAndAUniqueOneHasANamedConstraint() {
    String ddl = ddl("      a: {type: text, required: true, unique: true}\n");

    assertTrue(
        ddl.contains("\"a\" TEXT CONSTRAINT \"uq_item_a\" UNIQUE NOT NULL")
            || ddl.contains("\"a\" TEXT NOT NULL CONSTRAINT \"uq_item_a\" UNIQUE"),
        ddl);
  }

  @Test
  void aChoiceIsCheckedAgainstItsOptionsAndSeveralValuesAreAnArray() {
    String one = ddl("      k: {type: choice, options: [A, B]}\n");
    String many = ddl("      k: {type: choice, options: [A, B], multiple: true, default: [A]}\n");

    assertTrue(one.contains("CONSTRAINT \"ck_item_k\" CHECK (\"k\" IN ('A', 'B'))"), one);
    assertTrue(many.contains("\"k\" TEXT[] DEFAULT ARRAY['A']::text[]"), many);
    assertTrue(many.contains("CHECK (\"k\" <@ ARRAY['A', 'B']::text[])"), many);
  }

  @Test
  void aQuoteInAnOptionOrADefaultIsDoubled() {
    String ddl =
        ddl(
            "      k: {type: choice, options: [\"it's\"]}\n      d: {type: text, default: \"o'clock\"}\n");

    assertTrue(ddl.contains("IN ('it''s')"), ddl);
    assertTrue(ddl.contains("DEFAULT 'o''clock'"), ddl);
  }

  @Test
  void aNumberDefaultIsWrittenAsANumberAndBoundsAsChecks() {
    String ddl =
        ddl(
            "      n: {type: number, default: 5, min: 0, max: 100.5}\n      d: {type: date, min: 2026-01-01}\n");

    assertTrue(ddl.contains("\"n\" NUMERIC DEFAULT 5"), ddl);
    assertTrue(ddl.contains("CONSTRAINT \"ck_item_n$min\" CHECK (\"n\" >= 0)"), ddl);
    assertTrue(ddl.contains("CONSTRAINT \"ck_item_n$max\" CHECK (\"n\" <= 100.5)"), ddl);
    assertTrue(ddl.contains("CHECK (\"d\" >= DATE '2026-01-01')"), ddl);
  }

  @Test
  void aPatternMatchesTheWholeValue() {
    String ddl = ddl("      p: {type: text, pattern: \"[A-Z]{3}\"}\n");

    assertTrue(ddl.contains("CONSTRAINT \"ck_item_p$re\" CHECK (\"p\" ~ '^(?:[A-Z]{3})$')"), ddl);
    assertEquals("^(?:x|y)$", DdlGenerator.anchored("x|y"));
  }

  @Test
  void aBoundIsANumberAsWrittenOrATypedDate() {
    Model model = model("      n: {type: number}\n      d: {type: date}\n", "");
    Model.Entity item = model.entities().get("item");

    assertEquals("1.50", DdlGenerator.bound(item.field("n").get(), "1.50"));
    assertEquals("100", DdlGenerator.bound(item.field("n").get(), "1E+2"));
    assertEquals("DATE '2026-01-01'", DdlGenerator.bound(item.field("d").get(), "2026-01-01"));
  }

  @Test
  void aReferenceIsALinkToTheOtherTableAndAnIndexHelpsTheLookup() {
    String ddl = ddl("      p: {type: reference, to: parent}\n");

    assertTrue(ddl.contains("FOREIGN KEY"), ddl);
    assertTrue(ddl.contains("REFERENCES \"parent\""), ddl);
    assertTrue(ddl.contains("CREATE INDEX"), ddl);
  }

  @Test
  void everyTableIsReadableByTheReportRole() {
    String ddl = ddl("      a: {type: text}\n");

    assertTrue(ddl.contains("GRANT SELECT ON \"item\" TO \"report_ro\""), ddl);
    assertTrue(ddl.contains("GRANT SELECT ON \"parent\" TO \"report_ro\""), ddl);
  }

  @Test
  void aRenamedFieldKeepsTheColumnNameGivenForThePreview() {
    Model model = model("      new_name: {type: text, renamed_from: old_name}\n", "");

    String ddl = DdlGenerator.desiredState(model, Map.of("item", Map.of("new_name", "old_name")));

    assertTrue(ddl.contains("\"old_name\" TEXT"), ddl);
    assertFalse(ddl.contains("\"new_name\""), ddl);
  }

  @Test
  void renamingAFieldRenamesTheColumnItsConstraintsAndItsIndex() {
    Model model = model("      b: {type: text}\n", "");

    String sql = DdlGenerator.renameField(model, model.entities().get("item"), "a", "b");

    assertTrue(sql.startsWith("DO $$ BEGIN IF EXISTS"), sql);
    assertTrue(sql.contains("RENAME COLUMN \"a\" TO \"b\""), sql);
    assertTrue(sql.contains("RENAME CONSTRAINT \"uq_item_a\" TO \"uq_item_b\""), sql);
    assertTrue(sql.contains("RENAME CONSTRAINT \"ck_item_a$min\" TO \"ck_item_b$min\""), sql);
    assertTrue(sql.contains("RENAME CONSTRAINT \"fk_item_a\" TO \"fk_item_b\""), sql);
    assertTrue(sql.contains("ALTER INDEX IF EXISTS"), sql);
    assertTrue(sql.endsWith("END $$"), sql);
  }

  @Test
  void theSchemaIsCreatedAndOnlyDroppedWhenEmpty() {
    Model model = model("      a: {type: text}\n", "");

    assertEquals(
        "CREATE SCHEMA IF NOT EXISTS \"" + model.schema() + "\"", DdlGenerator.createSchema(model));
    assertEquals(
        "DROP SCHEMA IF EXISTS \"" + model.schema() + "\"", DdlGenerator.dropEmptySchema(model));
  }

  @Test
  void theSchemaAndTheReportRoleAreGrantedApart() {
    Model model = model("      a: {type: text}\n", "");

    assertTrue(
        DdlGenerator.grantSchemaUsage(model)
            .contains("GRANT USAGE ON SCHEMA \"" + model.schema() + "\""));
    String role = DdlGenerator.reportRole("secret'pw");
    assertTrue(role.contains("report_ro"), role);
    assertTrue(role.contains("secret''pw"), "a quote in the password is doubled: " + role);
  }
}
