// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import HaUI.ImplSolution.dxos.model.ModelException.Problem;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelParserTest {

  // Line numbers in the tests below count the lines of this file.
  private static final String MODEL =
      """
      format: 1
      name: loan
      roles:
        - name: BORROWER
        - name: KEEPER
          staff: true
      entities:
        item:
          label: Thiết bị
          fields:
            title: {type: text, label: Tên, required: true}
          access: {KEEPER: all, BORROWER: read}
        request:
          fields:
            item: {type: reference, to: item, label: Thiết bị}
            note: {type: text, label: Ghi chú}
          access: {BORROWER: own, KEEPER: all}
          process: {family: approval, handler: KEEPER}
      """;

  private static List<Problem> problems(String yaml) {
    return assertThrows(ModelException.class, () -> ModelParser.parse(yaml)).problems();
  }

  private static String replace(String from, String to) {
    assertTrue(MODEL.contains(from), from);
    return MODEL.replace(from, to);
  }

  // The problem of a file whose message holds the text; the test fails when there is none.
  private static Problem problem(String yaml, String text) {
    List<Problem> found = problems(yaml);
    return found.stream()
        .filter(p -> p.message().contains(text))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no problem says '" + text + "': " + found));
  }

  private static String entity(String fields, String rest) {
    return "format: 1\nname: x\nroles:\n  - name: U\nentities:\n  item:\n    fields:\n"
        + fields
        + "    access: {U: all}\n"
        + rest;
  }

  @Test
  void readsTheRolesEntitiesFieldsAndProcessOfAValidFile() {
    Model model = ModelParser.parse(MODEL);

    assertEquals("loan", model.name());
    assertEquals(
        List.of("BORROWER", "KEEPER"), model.roles().stream().map(Model.Role::name).toList());
    assertTrue(model.roles().get(1).staff());
    assertFalse(model.roles().get(0).staff());
    assertEquals(List.of("item", "request"), List.copyOf(model.entities().keySet()));
    Model.Entity request = model.entities().get("request");
    assertEquals(FieldType.REFERENCE, request.field("item").get().type());
    assertEquals("item", request.field("item").get().to());
    assertEquals("approval", request.process().family());
    assertEquals(Scope.OWN, request.access().get("BORROWER"));
    assertNull(model.entities().get("item").process());
  }

  @Test
  void aSyntaxErrorSaysWhereItIs() {
    List<Problem> found = problems("format: 1\nname: x\nroles: [\n");

    assertEquals(1, found.size());
    assertTrue(found.get(0).message().startsWith("invalid YAML"), found.get(0).message());
    assertTrue(found.get(0).line() > 0);
  }

  @Test
  void aFileThatIsNotAMappingIsRefused() {
    assertTrue(problem("- a\n- b\n", "must be a mapping").line() >= 0);
  }

  @Test
  void aFileNeedsRolesAndEntities() {
    List<Problem> found = problems("format: 1\nname: x\n");

    assertTrue(found.stream().anyMatch(p -> p.message().contains("roles is required")));
    assertTrue(found.stream().anyMatch(p -> p.message().contains("entities is required")));
  }

  @Test
  void aReferenceToAnEntityThatDoesNotExistIsRefusedAtItsLine() {
    Problem problem = problem(replace("to: item", "to: nothing"), "naming an entity");

    assertEquals(15, problem.line());
  }

  @Test
  void accessNamesOnlyRolesTheFileDeclares() {
    String yaml =
        replace(
            "access: {BORROWER: own, KEEPER: all}",
            "access: {BORROWER: own, KEEPER: all, GHOST: own}");

    assertEquals(17, problem(yaml, "role GHOST is not declared in roles").line());
  }

  @Test
  void anAccessScopeIsOneOfTheKnownOnes() {
    String yaml =
        replace(
            "access: {BORROWER: own, KEEPER: all}", "access: {BORROWER: everything, KEEPER: all}");

    assertEquals(
        17, problem(yaml, "access of BORROWER must be all, own, read, unit or assigned").line());
  }

  @Test
  void aRoleIsDeclaredOnce() {
    String yaml = replace("    staff: true\n", "    staff: true\n  - name: KEEPER\n");

    assertEquals(7, problem(yaml, "role KEEPER is declared twice").line());
  }

  @Test
  void aRoleNameIsUpperCase() {
    assertTrue(problems(replace("- name: BORROWER", "- name: borrower")).size() > 0);
  }

  @Test
  void aHandlerIsADeclaredRole() {
    String yaml = replace("handler: KEEPER", "handler: GHOST");

    assertEquals(18, problem(yaml, "handler must be a role declared in roles").line());
  }

  @Test
  void aHandlerCanSeeTheRecordsItHandles() {
    String yaml =
        replace("access: {BORROWER: own, KEEPER: all}", "access: {BORROWER: own, KEEPER: own}");

    assertEquals(18, problem(yaml, "KEEPER must have access all, read, unit or assigned").line());
  }

  @Test
  void aHandlerMayBeAListOfRoles() {
    Model model =
        ModelParser.parse(
            replace("handler: KEEPER", "handler: [KEEPER, BORROWER]")
                .replace("BORROWER: own, KEEPER: all}", "BORROWER: read, KEEPER: all}"));

    assertEquals(
        List.of("KEEPER", "BORROWER"), model.entities().get("request").process().handlers());
  }

  @Test
  void anActionCannotLeadToAStateThatDoesNotExist() {
    String yaml =
        replace(
            "process: {family: approval, handler: KEEPER}",
            "process: {family: approval, handler: KEEPER, actions: {approve: {to: nowhere}}}");

    Problem problem = problem(yaml, "nowhere");

    assertTrue(problem.message().contains("which is not a state"), problem.message());
  }

  @Test
  void aStateNothingLeadsToIsRefused() {
    String yaml =
        replace(
            "process: {family: approval, handler: KEEPER}",
            "process: {family: approval, handler: KEEPER, actions: {approve: null}}");

    assertNotNull(problem(yaml, "state approved can never be reached"));
  }

  @Test
  void aProcessWithoutAFamilyDeclaresItsOwnStatesAndActions() {
    String own =
        "process: {initial: new, handler: KEEPER, states: {new: {label: New}, done: {label: Done,"
            + " terminal: true}}, actions: {finish: {label: Finish, from: [new], to: done}}}";

    Model model = ModelParser.parse(replace("process: {family: approval, handler: KEEPER}", own));

    Model.Process process = model.entities().get("request").process();
    assertNull(process.family());
    assertEquals("new", process.definition().initial());
  }

  @Test
  void aCodeFieldNeedsAFormatWithASequence() {
    String code = "      code: {type: code, format: \"HS-{year}-{seq:5}\"}\n";
    String withCode =
        replace("      note: {type: text, label: Ghi chú}\n", "      note: {type: text}\n" + code);

    Model model = ModelParser.parse(withCode);

    assertEquals("HS-{year}-{seq:5}", model.entities().get("request").field("code").get().format());
    assertNotNull(problem(withCode.replace("HS-{year}-{seq:5}", "HS-{year}"), "{seq:N}"));
  }

  @Test
  void anEntityHasOneCodeFieldAtMost() {
    String two =
        entity(
            "      a: {type: code, format: \"A-{seq:3}\"}\n"
                + "      b: {type: code, format: \"B-{seq:3}\"}\n",
            "");

    assertNotNull(problem(two, "one code field at most"));
  }

  @Test
  void aChoiceFieldNeedsItsOptions() {
    Model model = ModelParser.parse(entity("      kind: {type: choice, options: [A, B]}\n", ""));

    assertEquals(List.of("A", "B"), model.entities().get("item").field("kind").get().options());
    assertTrue(problems(entity("      kind: {type: choice}\n", "")).size() > 0);
  }

  @Test
  void aFieldTypeIsOneOfTheKnownOnes() {
    assertNotNull(problem(entity("      a: {type: banana}\n", ""), "banana"));
  }

  @Test
  void aFieldNameIsLowerCaseWithUnderscores() {
    assertTrue(problems(entity("      Bad-Name: {type: text}\n", "")).size() > 0);
  }

  @Test
  void aRequiredWhenConditionNamesFieldsOfTheEntity() {
    String fields =
        "      amount: {type: number}\n      reason: {type: text, required_when: \"amount > 100\"}\n";
    Model model = ModelParser.parse(entity(fields, ""));

    assertNotNull(model.entities().get("item").field("reason").get().requiredWhen());
    assertNotNull(
        problem(
            entity(fields.replace("amount > 100", "ghost > 100"), ""),
            "required_when must name a number, date, choice, text or boolean field"));
  }

  @Test
  void aCalendarHasDaysHoursAndHolidays() {
    String yaml =
        "calendars:\n  office:\n    days: [mon, tue]\n    hours: ['08:00-12:00', '13:30-24:00']\n"
            + "    holidays: [2026-12-25]\n"
            + entity("      a: {type: text}\n", "").replace("format: 1\n", "format: 1\n");

    Model model = ModelParser.parse(yaml);
    Model.Calendar office = model.calendars().get("office");

    assertEquals(java.util.Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), office.days());
    assertEquals(
        List.of(
            new Model.TimeRange(LocalTime.of(8, 0), LocalTime.NOON),
            new Model.TimeRange(LocalTime.of(13, 30), LocalTime.MIDNIGHT)),
        office.hours());
    assertEquals(java.util.Set.of(LocalDate.of(2026, 12, 25)), office.holidays());
  }

  @Test
  void aProcessUsesACalendarTheFileDeclares() {
    String yaml =
        replace(
            "process: {family: approval, handler: KEEPER}",
            "process: {family: approval, handler: KEEPER, deadline: 2d, calendar: office}");

    assertEquals(18, problem(yaml, "unknown calendar").line());
  }

  @Test
  void everyProblemOfTheFileIsReportedTogether() {
    String broken =
        replace("to: item", "to: nothing")
            .replace(
                "access: {BORROWER: own, KEEPER: all}",
                "access: {BORROWER: own, KEEPER: all, GHOST: own}");

    List<Problem> found = problems(broken);

    assertTrue(found.stream().anyMatch(p -> p.message().contains("naming an entity")));
    assertTrue(found.stream().anyMatch(p -> p.message().contains("role GHOST")));
    assertEquals(2, found.size(), found.toString());
  }

  @Test
  void aKeyWrittenTwiceIsRefusedAtItsSecondLine() {
    assertEquals(3, problem("format: 1\nname: x\nname: y\n", "key name is written twice").line());
  }

  @Test
  void aFieldDeclaredTwiceIsRefusedInsteadOfKeepingTheLastOne() {
    String yaml =
        replace(
            "      note: {type: text, label: Ghi chú}\n",
            "      note: {type: text, label: Ghi chú}\n      note: {type: number}\n");

    assertEquals(17, problem(yaml, "key note is written twice").line());
  }

  @Test
  void aRoleWrittenTwiceInAnAccessIsRefusedSoALineOfItCannotVanish() {
    String yaml =
        replace(
            "access: {BORROWER: own, KEEPER: all}",
            "access: {BORROWER: own, KEEPER: all, BORROWER: read}");

    assertEquals(17, problem(yaml, "key BORROWER is written twice").line());
  }

  @Test
  void theSameKeyInDifferentMappingsIsNotARepeat() {
    String yaml =
        replace(
            "      note: {type: text, label: Ghi chú}\n",
            "      note: {type: text, label: Ghi chú}\n      title: {type: text}\n");

    assertEquals(
        List.of("item", "note", "title"),
        List.copyOf(ModelParser.parse(yaml).entities().get("request").fields().keySet()));
  }

  @Test
  void aStoredFileWithARepeatedKeyStillRunsAsItAlwaysDidAndIsReported() {
    String yaml =
        replace(
            "access: {BORROWER: own, KEEPER: all}",
            "access: {BORROWER: own, KEEPER: all, BORROWER: read}");

    ModelParser.ParsedStored stored = ModelParser.parseStored(yaml);

    assertEquals(Scope.READ, stored.model().entities().get("request").access().get("BORROWER"));
    assertEquals(1, stored.tolerated().size());
    assertTrue(stored.tolerated().get(0).message().contains("key BORROWER is written twice"));
    assertThrows(ModelException.class, () -> ModelParser.parse(yaml));
  }

  @Test
  void aStoredFileWithARepeatedKeyAndAnotherProblemIsStillRefused() {
    String yaml =
        replace("to: item", "to: nothing")
            .replace("format: 1\nname: loan\n", "format: 1\nname: loan\nname: loan\n");

    assertThrows(ModelException.class, () -> ModelParser.parseStored(yaml));
  }

  @Test
  void parseStoredKeepsAFileTheParserHasTightenedSinceItWasSaved() {
    ModelParser.ParsedStored stored = ModelParser.parseStored(MODEL);

    assertEquals("loan", stored.model().name());
    assertTrue(stored.tolerated().isEmpty());
    assertThrows(ModelException.class, () -> ModelParser.parseStored("format: 1\nname: x\n"));
  }
}
