// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import HaUI.ImplSolution.dxos.engine.apply.ModelDiff.Change;
import HaUI.ImplSolution.dxos.engine.apply.ModelDiff.Level;
import HaUI.ImplSolution.dxos.engine.apply.ModelDiff.Rule;
import HaUI.ImplSolution.dxos.model.ModelParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelDiffTest {

  private static final String FIELDS = "      a: {type: text}\n";
  private static final String PROCESS = "{family: approval, handler: A}";

  private static String model(String fields, String access, String process) {
    return "format: 1\nname: shop\nroles:\n  - name: A\n  - name: B\nentities:\n  item:\n"
        + "    fields:\n"
        + fields
        + "    access: "
        + access
        + "\n"
        + (process == null ? "" : "    process: " + process + "\n");
  }

  private static String fields(String fields) {
    return model(fields, "{A: all}", null);
  }

  private static String access(String access) {
    return model(FIELDS, access, null);
  }

  private static String process(String process) {
    return model(FIELDS, "{A: all, B: all}", process);
  }

  // A model whose access can name unit and assigned: it has a unit field and a process.
  private static String rich(String access) {
    return "format: 1\nname: shop\nunits:\n  - name: LAB\nroles:\n  - name: A\n  - name: B\n"
        + "entities:\n  item:\n    fields:\n      a: {type: text}\n      dept: {type: unit}\n"
        + "    access: "
        + access
        + "\n    process: {family: approval, handler: A}\n";
  }

  private static ModelDiff diff(String before, String after) {
    return ModelDiff.between(
        before == null ? null : ModelParser.parse(before), ModelParser.parse(after), Map.of());
  }

  private static Change change(ModelDiff diff, String kind) {
    return diff.changes.stream()
        .filter(c -> c.kind().equals(kind))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no " + kind + " in " + diff.changes));
  }

  private static List<String> kinds(ModelDiff diff) {
    return diff.changes.stream().map(Change::kind).toList();
  }

  @Test
  void aFileThatChangesNothingHasNoChanges() {
    ModelDiff diff = diff(fields(FIELDS), fields(FIELDS));

    assertTrue(diff.changes.isEmpty());
    assertTrue(diff.rejections.isEmpty());
    assertTrue(diff.checks.isEmpty());
    assertEquals(Level.SAFE, diff.level());
  }

  @Test
  void changingTheTaskEngineOrClassifierMakesASafeTaskChange() {
    String body =
        fields(FIELDS + "      kind: {type: choice, options: [A, B]}\n")
            + "    ai:\n      tasks:\n        classify:\n          input_fields: [a]\n          output_field: kind\n";
    String llm = body + "          instruction: Choose a kind\n          sources: [guide]\n";
    String local = body + "          engine: classifier\n          classifier: onestop\n";

    assertEquals(List.of(), kinds(diff(llm, llm + "          engine: llm\n")));
    assertEquals(Level.SAFE, change(diff(llm, local), "CHANGE_AI_TASKS").level());
    assertEquals(
        Level.SAFE,
        change(diff(local, local.replace("onestop", "support")), "CHANGE_AI_TASKS").level());
  }

  @Test
  void aFirstModelAddsItsRolesAndEntities() {
    ModelDiff diff = diff(null, fields(FIELDS));

    assertEquals(List.of("ADD_ROLE", "ADD_ROLE", "ADD_ENTITY"), kinds(diff));
    assertEquals(Level.SAFE, diff.level());
  }

  @Test
  void aNewEntityIsSafeAndADroppedOneLosesItsRows() {
    String two =
        fields(FIELDS) + "  other:\n    fields:\n      b: {type: text}\n    access: {A: all}\n";

    assertEquals(Level.SAFE, change(diff(fields(FIELDS), two), "ADD_ENTITY").level());

    ModelDiff dropped = diff(two, fields(FIELDS));
    assertEquals(Level.LOSS, change(dropped, "DROP_ENTITY").level());
    assertEquals(Level.LOSS, dropped.level());
    assertEquals(1, dropped.allowedDrops.size());
  }

  @Test
  void aNewRoleAndANewUnitAreSafe() {
    String before =
        "format: 1\nname: s\nroles:\n  - name: A\nentities:\n  i:\n    fields:\n"
            + FIELDS
            + "    access: {A: all}\n";
    String after =
        before
            .replace("  - name: A\n", "  - name: A\n  - name: C\n")
            .replace("roles:", "units:\n  - name: LAB\nroles:");

    ModelDiff diff = diff(before, after);

    assertEquals(List.of("ADD_ROLE", "ADD_UNIT"), kinds(diff));
    assertEquals(Level.SAFE, diff.level());
  }

  @Test
  void anOptionalFieldIsSafeAndNeedsNoRowCheck() {
    ModelDiff diff = diff(fields(FIELDS), fields(FIELDS + "      b: {type: number}\n"));

    assertEquals(Level.SAFE, change(diff, "ADD_FIELD").level());
    assertTrue(diff.checks.isEmpty());
  }

  @Test
  void aRequiredFieldNeedsEveryExistingRowToHoldAValue() {
    ModelDiff diff =
        diff(fields(FIELDS), fields(FIELDS + "      b: {type: text, required: true}\n"));

    assertEquals(Level.MIGRATION, change(diff, "ADD_FIELD").level());
    assertEquals(Rule.NEW_REQUIRED, diff.checks.get(0).rule());
    assertEquals("b", diff.checks.get(0).field());
  }

  @Test
  void aRequiredFieldWithADefaultFillsTheExistingRows() {
    ModelDiff diff =
        diff(
            fields(FIELDS),
            fields(FIELDS + "      b: {type: text, required: true, default: none}\n"));

    assertEquals(Level.MIGRATION, change(diff, "ADD_FIELD").level());
    assertTrue(diff.checks.isEmpty());
  }

  @Test
  void aCodeFieldGivesEveryExistingRecordACode() {
    ModelDiff diff =
        diff(fields(FIELDS), fields(FIELDS + "      code: {type: code, format: \"X-{seq:3}\"}\n"));

    Change add = change(diff, "ADD_FIELD");
    assertEquals(Level.MIGRATION, add.level());
    assertEquals("Existing records receive a code", add.detail());
  }

  @Test
  void aChangedCodeFormatIsSafe() {
    String code = "      code: {type: code, format: \"X-{seq:3}\"}\n";

    ModelDiff diff = diff(fields(code), fields(code.replace("X-", "Y-")));

    assertEquals(Level.SAFE, change(diff, "CHANGE_FORMAT").level());
  }

  @Test
  void aDroppedFieldLosesItsValues() {
    ModelDiff diff = diff(fields(FIELDS + "      b: {type: text}\n"), fields(FIELDS));

    Change drop = change(diff, "DROP_FIELD");
    assertEquals(Level.LOSS, drop.level());
    assertEquals("b", drop.field());
    assertEquals(Level.LOSS, diff.level());
    assertEquals(1, diff.allowedDrops.size());
    assertTrue(diff.allowedDrops.iterator().next().endsWith("item.b"));
  }

  @Test
  void aDropAndAnAddOfTheSameTypeAsksWhetherItIsARename() {
    ModelDiff diff =
        diff(
            fields(FIELDS + "      old: {type: text}\n"),
            fields(FIELDS + "      new: {type: text}\n"));

    assertEquals(
        List.of("Is item.new a rename of old? Then declare renamed_from: old to keep the values"),
        diff.questions);
  }

  @Test
  void renamedFromKeepsTheRowsAndIsAMigration() {
    ModelDiff diff =
        diff(
            fields(FIELDS + "      old: {type: text}\n"),
            fields(FIELDS + "      new: {type: text, renamed_from: old}\n"));

    assertEquals(Level.MIGRATION, change(diff, "RENAME_FIELD").level());
    assertFalse(kinds(diff).contains("DROP_FIELD"));
    assertEquals(Map.of("item", Map.of("new", "old")), diff.renames);
  }

  @Test
  void aRenameFromTheOutsideKeepsTheRowsOfARollback() {
    ModelDiff diff =
        ModelDiff.between(
            ModelParser.parse(fields(FIELDS + "      new: {type: text}\n")),
            ModelParser.parse(fields(FIELDS + "      old: {type: text}\n")),
            Map.of("item", Map.of("old", "new")));

    assertEquals(Level.MIGRATION, change(diff, "RENAME_FIELD").level());
    assertFalse(kinds(diff).contains("DROP_FIELD"));
  }

  @Test
  void aFieldCannotChangeItsType() {
    ModelDiff diff = diff(fields("      a: {type: text}\n"), fields("      a: {type: number}\n"));

    assertEquals(1, diff.rejections.size());
    assertTrue(diff.rejections.get(0).contains("changes its type from text to number"));
  }

  @Test
  void aFieldCannotChangeBetweenOneValueAndSeveral() {
    String one = "      k: {type: choice, options: [A, B]}\n";
    String several = "      k: {type: choice, options: [A, B], multiple: true}\n";

    assertTrue(diff(fields(one), fields(several)).rejections.get(0).contains("one value and"));
  }

  @Test
  void aReferenceCannotPointAtAnotherEntity() {
    String two =
        "  other:\n    fields:\n      b: {type: text}\n    access: {A: all}\n"
            + "  third:\n    fields:\n      c: {type: text}\n    access: {A: all}\n";
    String toOther = model("      r: {type: reference, to: other}\n", "{A: all}", null) + two;
    String toThird = toOther.replace("to: other", "to: third");

    assertTrue(diff(toOther, toThird).rejections.get(0).contains("refers to another entity"));
  }

  @Test
  void makingAFieldRequiredNeedsTheRowsToFitAndUndoingItIsSafe() {
    ModelDiff on = diff(fields(FIELDS), fields("      a: {type: text, required: true}\n"));

    assertEquals(Level.MIGRATION, change(on, "ADD_REQUIRED").level());
    assertEquals(Rule.REQUIRED, on.checks.get(0).rule());
    assertEquals("a", on.checks.get(0).column());

    ModelDiff off = diff(fields("      a: {type: text, required: true}\n"), fields(FIELDS));
    assertEquals(Level.SAFE, change(off, "DROP_REQUIRED").level());
    assertTrue(off.checks.isEmpty());
  }

  @Test
  void makingAFieldUniqueNeedsTheRowsToBeDistinct() {
    ModelDiff on = diff(fields(FIELDS), fields("      a: {type: text, unique: true}\n"));

    assertEquals(Level.MIGRATION, change(on, "ADD_UNIQUE").level());
    assertEquals(Rule.UNIQUE, on.checks.get(0).rule());
    assertEquals(
        Level.SAFE,
        change(diff(fields("      a: {type: text, unique: true}\n"), fields(FIELDS)), "DROP_UNIQUE")
            .level());
  }

  @Test
  void fewerOptionsNeedTheRowsToFitAndMoreOptionsAreSafe() {
    String two = "      k: {type: choice, options: [A, B]}\n";
    String one = "      k: {type: choice, options: [A]}\n";
    String three = "      k: {type: choice, options: [A, B, C]}\n";

    ModelDiff fewer = diff(fields(two), fields(one));
    assertEquals(Level.MIGRATION, change(fewer, "CHANGE_OPTIONS").level());
    assertEquals(Rule.OPTIONS, fewer.checks.get(0).rule());
    assertEquals(List.of("A"), fewer.checks.get(0).options());

    ModelDiff more = diff(fields(two), fields(three));
    assertEquals(Level.SAFE, change(more, "CHANGE_OPTIONS").level());
    assertTrue(more.checks.isEmpty());
  }

  @Test
  void fewerOptionsOfAMultipleChoiceCheckEachElement() {
    String two = "      k: {type: choice, options: [A, B], multiple: true}\n";
    String one = "      k: {type: choice, options: [A], multiple: true}\n";

    assertEquals(Rule.ELEMENTS, diff(fields(two), fields(one)).checks.get(0).rule());
  }

  @Test
  void aTighterBoundNeedsTheRowsToFitAndALooserOneIsSafe() {
    String wide = "      n: {type: number, min: 0, max: 100}\n";

    ModelDiff higherMin = diff(fields(wide), fields(wide.replace("min: 0", "min: 10")));
    assertEquals(Level.MIGRATION, change(higherMin, "CHANGE_MIN").level());
    assertEquals(Rule.MIN, higherMin.checks.get(0).rule());

    ModelDiff lowerMax = diff(fields(wide), fields(wide.replace("max: 100", "max: 50")));
    assertEquals(Level.MIGRATION, change(lowerMax, "CHANGE_MAX").level());
    assertEquals(Rule.MAX, lowerMax.checks.get(0).rule());

    ModelDiff looser = diff(fields(wide), fields(wide.replace("max: 100", "max: 500")));
    assertEquals(Level.SAFE, change(looser, "CHANGE_MAX").level());
    assertTrue(looser.checks.isEmpty());
  }

  @Test
  void aBoundOnADateIsComparedAsADate() {
    String early = "      d: {type: date, min: 2026-01-01}\n";
    String late = "      d: {type: date, min: 2026-06-01}\n";

    assertEquals(Level.MIGRATION, change(diff(fields(early), fields(late)), "CHANGE_MIN").level());
    assertEquals(Level.SAFE, change(diff(fields(late), fields(early)), "CHANGE_MIN").level());
  }

  @Test
  void addingABoundNeedsTheRowsToFitAndDroppingItIsSafe() {
    String none = "      n: {type: number}\n";
    String bound = "      n: {type: number, max: 9}\n";

    assertEquals(Level.MIGRATION, change(diff(fields(none), fields(bound)), "CHANGE_MAX").level());
    ModelDiff dropped = diff(fields(bound), fields(none));
    assertEquals(Level.SAFE, change(dropped, "DROP_MAX").level());
    assertTrue(dropped.checks.isEmpty());
  }

  @Test
  void aPatternNeedsTheRowsToFitAndDroppingItIsSafe() {
    String none = "      p: {type: text}\n";
    String pattern = "      p: {type: text, pattern: \"[A-Z]+\"}\n";

    ModelDiff added = diff(fields(none), fields(pattern));
    assertEquals(Level.MIGRATION, change(added, "CHANGE_PATTERN").level());
    assertEquals(Rule.PATTERN, added.checks.get(0).rule());
    assertEquals(Level.SAFE, change(diff(fields(pattern), fields(none)), "DROP_PATTERN").level());
  }

  @Test
  void aConditionalRequirementNeedsTheRowsToFitAndDroppingItIsSafe() {
    String base = "      amount: {type: number}\n      why: {type: text}\n";
    String when =
        base.replace("why: {type: text}", "why: {type: text, required_when: \"amount > 10\"}");

    ModelDiff added = diff(fields(base), fields(when));
    assertEquals(Level.MIGRATION, change(added, "CHANGE_REQUIRED_WHEN").level());
    assertEquals(Rule.REQUIRED_WHEN, added.checks.get(0).rule());
    assertEquals(
        Level.SAFE, change(diff(fields(when), fields(base)), "DROP_REQUIRED_WHEN").level());
  }

  @Test
  void aLabelOrADefaultIsSafe() {
    ModelDiff diff =
        diff(
            fields("      a: {type: text, label: One}\n"),
            fields("      a: {type: text, label: Two, default: x}\n"));

    assertEquals(List.of("CHANGE_LABEL", "CHANGE_DEFAULT"), kinds(diff));
    assertEquals(Level.SAFE, diff.level());
  }

  @Test
  void aRoleThatGainsAccessNeedsAcceptAndAsksAboutIt() {
    ModelDiff grant = diff(access("{A: all}"), access("{A: all, B: read}"));
    assertEquals("GRANT_ACCESS", grant.changes.get(0).kind());
    assertEquals(Level.MIGRATION, grant.level());
    assertEquals(
        List.of(
            "role B gets wider access to item (none → read); applying gives it at once to"
                + " everyone who holds the role"),
        grant.questions);

    ModelDiff widen = diff(access("{A: all, B: own}"), access("{A: all, B: all}"));
    assertEquals(Level.MIGRATION, change(widen, "WIDEN_ACCESS").level());
  }

  @Test
  void aRoleThatLosesAccessIsSafe() {
    ModelDiff revoke = diff(access("{A: all, B: read}"), access("{A: all}"));
    assertEquals(Level.SAFE, change(revoke, "REVOKE_ACCESS").level());
    assertTrue(revoke.questions.isEmpty());

    assertEquals(
        Level.SAFE,
        change(diff(access("{A: all, B: all}"), access("{A: all, B: read}")), "NARROW_ACCESS")
            .level());
    assertEquals(
        Level.SAFE,
        change(diff(rich("{A: all, B: unit}"), rich("{A: all, B: own}")), "NARROW_ACCESS").level());
  }

  @Test
  void aMoveThatGainsOnOneCountAndLosesOnTheOtherIsOnlyReported() {
    ModelDiff readToOwn = diff(access("{A: all, B: read}"), access("{A: all, B: own}"));
    assertEquals(Level.SAFE, change(readToOwn, "CHANGE_ACCESS").level());

    ModelDiff ownToAssigned = diff(rich("{A: all, B: own}"), rich("{A: all, B: assigned}"));
    assertEquals(Level.SAFE, change(ownToAssigned, "CHANGE_ACCESS").level());
  }

  @Test
  void aChangedOwnerLinkLabelAndTheRestOfAnEntityAreSafe() {
    String before = model(FIELDS, "{A: all}", null);
    String after = before.replace("  item:\n", "  item:\n    label: Items\n");

    assertEquals(List.of("CHANGE_LABEL"), kinds(diff(before, after)));
  }

  @Test
  void addingAProcessNeedsAnEmptyEntityAndDroppingItToo() {
    ModelDiff add = diff(process(null), process(PROCESS));
    assertEquals(Level.MIGRATION, change(add, "ADD_PROCESS").level());
    assertEquals("Only for an entity without rows", change(add, "ADD_PROCESS").detail());
    assertEquals(Rule.REQUIRED, add.checks.get(0).rule());

    assertEquals(
        Level.MIGRATION, change(diff(process(PROCESS), process(null)), "DROP_PROCESS").level());
  }

  @Test
  void aProcessCannotSwitchToAnotherFamily() {
    ModelDiff diff = diff(process(PROCESS), process("{family: work_item, handler: A}"));

    assertTrue(diff.rejections.get(0).contains("changes its process family"));
  }

  @Test
  void theHandlersTheDeadlineAndTheOtherParametersOfAProcessAreSafe() {
    ModelDiff diff =
        diff(
            process(PROCESS),
            process(
                "{family: approval, handler: [A, B], deadline: 2d, allow_self_decision: true,"
                    + " auto_close: {after: 7d, action: resubmit}}"));

    assertEquals(
        List.of("CHANGE_HANDLER", "CHANGE_SELF_DECISION", "CHANGE_DEADLINE", "CHANGE_AUTO_CLOSE"),
        kinds(diff));
    assertEquals(Level.SAFE, diff.level());
    assertEquals("A, B", change(diff, "CHANGE_HANDLER").detail());
  }

  @Test
  void aChangedCalendarIsReportedWithItsProcessOrAloneWhenNoProcessUsesIt() {
    String calendars = "calendars:\n  office:\n    days: [mon]\n    hours: ['08:00-12:00']\n";
    String other = calendars.replace("[mon]", "[tue]");

    ModelDiff alone = diff(calendars + fields(FIELDS), other + fields(FIELDS));
    assertEquals(List.of("CHANGE_CALENDAR"), kinds(alone));
    assertNull(alone.changes.get(0).entity());

    String used = "{family: approval, handler: A, deadline: 8h, calendar: office}";
    ModelDiff withProcess = diff(calendars + process(used), other + process(used));
    assertEquals("item", change(withProcess, "CHANGE_CALENDAR").entity());
  }

  @Test
  void stepsOfTheProcessThatChangeAreReportedAndCheckTheStatusOfTheRows() {
    String own =
        "{family: approval, handler: A, states: {extra: {label: Extra}}, actions: {go: {label:"
            + " Go, from: [submitted], to: extra}, back: {label: Back, from: [extra], to:"
            + " submitted}}}";

    ModelDiff diff = diff(process(PROCESS), process(own));

    assertEquals(Level.SAFE, change(diff, "CHANGE_FLOW").level());
    assertEquals("status", diff.checks.get(0).column());
    assertEquals(Rule.OPTIONS, diff.checks.get(0).rule());
    assertTrue(diff.checks.get(0).options().contains("extra"));
  }

  @Test
  void aFinalStateThatStopsBeingFinalCannotHoldRows() {
    String reopen =
        "{family: approval, handler: A, states: {rejected: {terminal: false}},"
            + " actions: {again: {label: Again, from: [rejected], to: submitted}}}";

    ModelDiff diff = diff(process(PROCESS), process(reopen));

    assertFalse(diff.checks.get(0).options().contains("rejected"));
    assertTrue(diff.checks.get(0).options().contains("approved"));
  }

  @Test
  void theRolesOfAnActionAndTheFieldsItRequiresApplyAtOnce() {
    String roles = "{family: approval, handler: [A, B], actions: {approve: {role: B}}}";
    String fields = FIELDS + "      note: {type: text}\n";
    String require = "{family: approval, handler: A, actions: {approve: {require: [note]}}}";

    ModelDiff byRole =
        diff(
            model(FIELDS, "{A: all, B: all}", "{family: approval, handler: [A, B]}"),
            model(FIELDS, "{A: all, B: all}", roles));
    assertEquals(Level.SAFE, change(byRole, "CHANGE_ACTIONS").level());
    assertTrue(change(byRole, "CHANGE_ACTIONS").detail().contains("approve: roles"));

    ModelDiff byRequire =
        diff(model(fields, "{A: all}", PROCESS), model(fields, "{A: all}", require));
    assertEquals("approve: require +note", change(byRequire, "CHANGE_ACTIONS").detail());
    assertEquals(1, byRequire.requireGains.size());
    assertEquals(List.of("note"), byRequire.requireGains.get(0).fields());
    assertEquals("approve", byRequire.requireGains.get(0).action());
  }

  @Test
  void theLevelOfAPlanIsTheHighestOfItsChanges() {
    ModelDiff diff =
        diff(
            fields(FIELDS + "      b: {type: text}\n"),
            fields(
                "      a: {type: text, label: New}\n      c: {type: number}\n      d: {type: text, required: true}\n"));

    assertTrue(kinds(diff).containsAll(List.of("CHANGE_LABEL", "ADD_FIELD", "DROP_FIELD")));
    assertEquals(Level.LOSS, diff.level());
  }
}
