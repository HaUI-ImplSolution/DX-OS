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
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

// The blocks of an entity other than its fields and access: the assistant, the print templates,
// the look of the screens, and the parameters of a process.
class ModelParserSectionsTest {

  private static final String FIELDS =
      "      title: {type: text, label: Tên}\n      note: {type: text}\n"
          + "      amount: {type: number}\n      scan: {type: file}\n"
          + "      kind: {type: choice, options: [A, B]}\n"
          + "      kinds: {type: choice, multiple: true, options: [A, B]}\n";

  // An entity with the fields above and `rest` written under it at the indent of an entity key.
  private static String entity(String rest) {
    return "format: 1\nname: x\nroles:\n  - name: A\n  - name: B\nentities:\n  item:\n"
        + "    fields:\n"
        + FIELDS
        + "    access: {A: all, B: read}\n"
        + rest;
  }

  private static Model ok(String rest) {
    return ModelParser.parse(entity(rest));
  }

  private static List<Problem> problems(String rest) {
    return assertThrows(ModelException.class, () -> ModelParser.parse(entity(rest))).problems();
  }

  private static void refused(String rest, String text) {
    List<Problem> found = problems(rest);
    assertTrue(
        found.stream().anyMatch(p -> p.message().contains(text)),
        "expected '" + text + "' in " + found);
  }

  private static String task(String body) {
    return "    ai:\n      tasks:\n        t1:\n" + body;
  }

  private static final String GOOD_TASK =
      "          label: Gợi ý\n          input_fields: [title, note]\n"
          + "          output_field: note\n          instruction: Hãy tóm tắt\n          sources: [kb_v1]\n";
  private static final String CLASSIFIER_TASK =
      "          engine: classifier\n          classifier: support\n"
          + "          input_fields: [title, note]\n          output_field: kind\n";

  // --- the assistant ------------------------------------------------------------------------

  @Test
  void anAssistantTaskNamesItsInputsAndTheFieldItFills() {
    Model.AiTask task =
        ok(task(GOOD_TASK + "          max_length: 300\n"))
            .entities()
            .get("item")
            .aiTasks()
            .get("t1");

    assertEquals("Gợi ý", task.label());
    assertEquals(List.of("title", "note"), task.inputFields());
    assertEquals("note", task.outputField());
    assertEquals(300, task.maxLength());
    assertEquals("llm", task.engine());
    assertNull(task.classifier());
  }

  @Test
  void aClassifierTaskUsesTextInputsAndASingleChoiceOutput() {
    var task =
        ok(task(CLASSIFIER_TASK + "          max_length: 1\n"))
            .entities()
            .get("item")
            .aiTasks()
            .get("t1");

    assertEquals("classifier", task.engine());
    assertEquals("support", task.classifier());
    assertEquals(List.of("title", "note"), task.inputFields());
    assertEquals("kind", task.outputField());
    assertEquals(List.of(), task.sources());
    assertNull(task.maxLength());
    assertEquals(
        "Metadata only",
        ok(task(CLASSIFIER_TASK + "          instruction: Metadata only\n"))
            .entities()
            .get("item")
            .aiTasks()
            .get("t1")
            .instruction());
  }

  @Test
  void classifierAndLlmTasksEnforceTheirOwnSettings() {
    refused(task(GOOD_TASK + "          engine: unknown\n"), "engine must be llm or classifier");
    refused(
        task(GOOD_TASK + "          classifier: support\n"),
        "llm tasks must not declare classifier");
    refused(
        task(CLASSIFIER_TASK.replace("          classifier: support\n", "")),
        "classifier is required");
    refused(
        task(CLASSIFIER_TASK.replace("classifier: support", "classifier: support-desk")),
        "classifier must match");
    refused(
        task(CLASSIFIER_TASK.replace("classifier: support", "classifier: null")),
        "classifier must be a name");
    refused(
        task(CLASSIFIER_TASK + "          sources: []\n"),
        "classifier tasks must not declare sources");
    refused(
        task(CLASSIFIER_TASK.replace("[title, note]", "[title, amount]")),
        "classifier input_fields must name text fields");
    for (String field : List.of("note", "kinds")) {
      refused(
          task(CLASSIFIER_TASK.replace("output_field: kind", "output_field: " + field)),
          "classifier output_field must name a single choice field");
    }
  }

  @Test
  void anAssistantTaskNeedsItsInputsALabelAndAnInstruction() {
    refused(task("          label: x\n          output_field: note\n"), "input_fields");
    refused(task(GOOD_TASK.replace("          instruction: Hãy tóm tắt\n", "")), "instruction");
    refused(
        task(GOOD_TASK.replace("          sources: [kb_v1]\n", "")),
        "sources must contain between 1 and 12 names");
  }

  @Test
  void anAssistantTaskReadsOnlyFieldsItCanReadAndFillsATextOrChoice() {
    refused(
        task(GOOD_TASK.replace("[title, note]", "[title, scan]")),
        "input_fields must name supported fields");
    refused(
        task(GOOD_TASK.replace("output_field: note", "output_field: amount")),
        "output_field must name a text or single choice field");
  }

  @Test
  void anAssistantTaskLengthIsBetweenOneAndTwoThousand() {
    refused(
        task(GOOD_TASK + "          max_length: 5000\n"),
        "max_length must be a whole number between 1 and 2000");
    refused(task(GOOD_TASK + "          max_length: 0\n"), "max_length");
  }

  @Test
  void anAssistantHasOneToEightTasks() {
    refused("    ai:\n      tasks: {}\n", "between 1 and 8 tasks");
    StringBuilder nine = new StringBuilder("    ai:\n      tasks:\n");
    for (int i = 0; i < 9; i++) {
      nine.append("        t")
          .append(i)
          .append(":\n")
          .append(GOOD_TASK.replace("          ", "          "));
    }
    refused(nine.toString(), "between 1 and 8 tasks");
  }

  @Test
  void aTaskIsDeclaredOnce() {
    refused(task(GOOD_TASK) + "        t1:\n" + GOOD_TASK, "task t1 is declared twice");
  }

  // --- print templates ----------------------------------------------------------------------

  private static String document(String body) {
    return "    documents:\n      result:\n        label: Kết quả\n"
        + "        title: GIẤY {{title}}\n        body:\n"
        + body;
  }

  @Test
  void aPrintTemplateHasATitleAndBlocks() {
    Model.Document document =
        ok(document(
                "          - heading: Thông tin\n          - text: Hồ sơ {{title}}\n"
                    + "          - fields: [title, amount]\n          - signatures: [Người nộp, Cán bộ]\n"))
            .entities()
            .get("item")
            .documents()
            .get(0);

    assertEquals("result", document.name());
    assertEquals("result", document.file(), "the file defaults to the name");
    assertEquals("GIẤY {{title}}", document.title());
    assertEquals(4, document.body().size());
    assertTrue(document.body().get(0) instanceof Model.Document.Heading);
    assertTrue(document.body().get(2) instanceof Model.Document.Fields);
    assertEquals(
        List.of("Người nộp", "Cán bộ"),
        ((Model.Document.Signatures) document.body().get(3)).captions());
  }

  @Test
  void aPrintTemplateNeedsABody() {
    refused("    documents:\n      result:\n        label: Kết quả\n", "needs a body");
  }

  @Test
  void aBlockHasOneKeyOfTheKnownKinds() {
    refused(document("          - heading: A\n            text: B\n"), "one key");
    refused(document("          - picture: A\n"), "picture");
    refused(document("          heading: not a list\n"), "body is a list of blocks");
  }

  @Test
  void aPlaceholderNamesAFieldOfTheEntityOrAColumn() {
    refused(document("          - text: Hồ sơ {{ghost}}\n"), "{{ghost}}");
    refused(document("          - fields: [title, ghost]\n"), "fields names ghost");
    refused(
        document("          - text: broken {{Title}}\n"),
        "a placeholder is written {{field_name}}");
    assertNotNull(ok(document("          - text: Mã {{id}}\n")));
  }

  @Test
  void aPrintTemplateIsLimitedToStatesOfTheProcessOfTheEntity() {
    refused(
        "    documents:\n      result:\n        when: [approved]\n        body:\n          - text: x\n",
        "when names states, and this entity has no process");
  }

  @Test
  void aSignatureListHasOneToFourCaptions() {
    refused(
        document("          - signatures: [a, b, c, d, e]\n"),
        "signatures is a list of 1 to 4 captions");
    refused(document("          - signatures: []\n"), "signatures is a list of 1 to 4 captions");
  }

  @Test
  void aTextHasNoControlCharactersAndIsNotTooLong() {
    refused(document("          - text: \"a\\u0001b\"\n"), "control characters");
    refused(
        document("          - text: \"" + "x".repeat(2001) + "\"\n"),
        "a text is at most 2000 characters");
  }

  @Test
  void anEntityHasAtMostTenPrintTemplates() {
    StringBuilder many = new StringBuilder("    documents:\n");
    for (int i = 0; i < 11; i++) {
      many.append("      d").append(i).append(":\n        body:\n          - text: x\n");
    }

    refused(many.toString(), "at most 10 documents");
  }

  // --- the look of the screens --------------------------------------------------------------

  @Test
  void theLookOfAnEntityIsChosenAmongItsFields() {
    Model.Ui ui =
        ok("    ui:\n      icon: person\n      menu: {group: Quản trị, order: 2}\n"
                + "      list: {columns: [title, amount], sort: \"-amount\"}\n"
                + "      form:\n        groups:\n          - {label: Chính, fields: [title]}\n"
                + "      hints: {title: Gõ tên đầy đủ}\n")
            .entities()
            .get("item")
            .ui();

    assertEquals("person", ui.icon());
    assertEquals("Quản trị", ui.menu().group());
    assertEquals(List.of("title", "amount"), ui.list().columns());
    assertEquals("-amount", ui.list().sort());
    assertEquals("Gõ tên đầy đủ", ui.hints().get("title"));
  }

  @Test
  void anUnknownIconIsRefused() {
    refused("    ui:\n      icon: rocket\n", "unknown icon rocket");
  }

  @Test
  void aListShowsOnlyColumnsThatExist() {
    refused("    ui:\n      list: {columns: [title, ghost]}\n", "ghost");
    refused("    ui:\n      list: {columns: []}\n", "non-empty list of field names");
  }

  @Test
  void aHintBelongsToAFieldAndIsShort() {
    refused("    ui:\n      hints: {ghost: Gợi ý}\n", "field ghost is not a field of this entity");
    refused(
        "    ui:\n      hints: {title: \"" + "x".repeat(201) + "\"}\n",
        "a hint is text of 1 to 200 characters");
  }

  @Test
  void aFormGroupNamesFieldsOfTheEntity() {
    refused(
        "    ui:\n      form:\n        groups:\n          - {label: G, fields: [ghost]}\n",
        "field ghost is not a field of this entity");
  }

  @Test
  void aMenuGroupIsShortText() {
    refused(
        "    ui:\n      menu: {group: \"" + "x".repeat(41) + "\"}\n",
        "the menu group is text of 1 to 40 characters");
  }

  // --- the parameters of a process ----------------------------------------------------------

  private static String process(String process) {
    return "    process: " + process + "\n";
  }

  private static Model.Process processOf(String process) {
    return ok(process(process)).entities().get("item").process();
  }

  @Test
  void aDeadlineIsInMinutesHoursOrDays() {
    assertEquals(
        Duration.ofMinutes(90),
        processOf("{family: approval, handler: A, deadline: 90m}").deadline());
    assertEquals(
        Duration.ofHours(8), processOf("{family: approval, handler: A, deadline: 8h}").deadline());
    Model.Process days = processOf("{family: approval, handler: A, deadline: 3d}");
    assertEquals(Duration.ofDays(3), days.deadline());
    assertTrue(days.deadlineInDays());
    assertFalse(processOf("{family: approval, handler: A, deadline: 8h}").deadlineInDays());
  }

  @Test
  void aDeadlineThatIsNotADurationIsRefused() {
    refused(process("{family: approval, handler: A, deadline: soon}"), "deadline");
    refused(process("{family: approval, handler: A, deadline: 0h}"), "deadline");
  }

  @Test
  void aProcessMayLetItsCreatorDecide() {
    assertTrue(
        processOf("{family: approval, handler: A, allow_self_decision: true}").allowSelfDecision());
    assertFalse(processOf("{family: approval, handler: A}").allowSelfDecision());
  }

  @Test
  void anEscalationNeedsADeadlineAndDeclaredRoles() {
    Model.Process process =
        processOf(
            "{family: approval, handler: A, deadline: 24h, escalate: [{after: 12h, to: B},"
                + " {after: 24h, to: [A, B], repeat: {every: 6h, times: 3}}]}");

    assertEquals(2, process.escalations().size());
    assertEquals(List.of("B"), process.escalations().get(0).to());
    refused(process("{family: approval, handler: A, escalate: [{after: 12h, to: B}]}"), "escalate");
    refused(
        process(
            "{family: approval, handler: A, deadline: 24h, escalate: [{after: 12h, to: GHOST}]}"),
        "to must be a role declared in roles");
  }

  @Test
  void aWaitingRecordClosesByItselfAfterATime() {
    Model.Process process =
        processOf("{family: work_item, handler: A, auto_close: {after: 7d, action: confirm}}");

    assertEquals(Duration.ofDays(7), process.autoClose());
    assertEquals("confirm", process.autoCloseAction());
    assertEquals(
        Duration.ofDays(7),
        processOf("{family: work_item, handler: A, auto_close: 7d}").autoClose());
  }

  @Test
  void aFinishedRecordCanAddAPersonToAGroup() {
    Model.Process process =
        processOf(
            "{family: approval, handler: A, after: {approved: [{do: add_to_group, user: created_by,"
                + " group: B}]}}");

    assertEquals(1, process.after().get("approved").size());
  }

  @Test
  void aCompletionBelongsToAFinalState() {
    refused(
        process(
            "{family: approval, handler: A, after: {submitted: [{do: add_to_group, user: created_by, group: B}]}}"),
        "submitted is not a final state of the process");
  }

  @Test
  void aBookingNeedsAResourceAndATimeSpan() {
    String fields =
        "      room: {type: reference, to: room}\n      from_at: {type: datetime}\n"
            + "      to_at: {type: datetime}\n";
    String model =
        "format: 1\nname: x\nroles:\n  - name: A\nentities:\n  room:\n    fields:\n"
            + "      name: {type: text}\n    access: {A: all}\n  booking:\n    fields:\n"
            + fields
            + "    access: {A: all}\n    process: {family: reservation, handler: A, booking:"
            + " {resource: room, starts: from_at, ends: to_at, capacity: 2}}\n";

    Booking booking = ModelParser.parse(model).entities().get("booking").process().booking();

    assertEquals("room", booking.resource());
    assertEquals("from_at", booking.starts());
    assertEquals(2, booking.capacity());
    assertThrows(
        ModelException.class, () -> ModelParser.parse(model.replace("starts: from_at, ", "")));
    assertThrows(
        ModelException.class,
        () -> ModelParser.parse(model.replace("resource: room", "resource: ghost")));
  }

  @Test
  void aProcessIsFreeOfBookingUnlessItIsAReservation() {
    assertThrows(
        ModelException.class,
        () ->
            ModelParser.parse(
                entity(
                    process(
                        "{family: approval, handler: A, booking: {resource: title, starts: title,"
                            + " ends: title}}"))));
  }

  // --- units --------------------------------------------------------------------------------

  @Test
  void aUnitFieldNeedsDeclaredUnitsAndUnitAccessNeedsAUnitField() {
    String units =
        "format: 1\nname: x\nunits:\n  - {name: LAB, label: Phòng thí nghiệm}\nroles:\n  - name: A\n";
    String withUnit =
        units
            + "entities:\n  item:\n    fields:\n      dept: {type: unit}\n    access: {A: unit}\n";

    Model model = ModelParser.parse(withUnit);

    assertEquals("LAB", model.units().get(0).name());
    assertEquals(Scope.UNIT, model.entities().get("item").access().get("A"));
    assertTrue(
        assertThrows(
                ModelException.class,
                () ->
                    ModelParser.parse(
                        entity("").replace("access: {A: all, B: read}", "access: {A: unit}")))
            .getMessage()
            .contains("needs a unit field"));
  }

  @Test
  void aUnitIsDeclaredOnce() {
    String twice =
        "format: 1\nname: x\nunits:\n  - name: LAB\n  - name: LAB\nroles:\n  - name: A\n"
            + "entities:\n  item:\n    fields:\n      a: {type: text}\n    access: {A: all}\n";

    assertTrue(
        assertThrows(ModelException.class, () -> ModelParser.parse(twice))
            .getMessage()
            .contains("unit LAB is declared twice"));
  }

  // --- what a field can carry ---------------------------------------------------------------

  private static void fieldRefused(String field, String text) {
    List<Problem> found =
        assertThrows(
                ModelException.class,
                () ->
                    ModelParser.parse(
                        entity("").replace("      note: {type: text}\n", "      " + field + "\n")))
            .problems();
    assertTrue(
        found.stream().anyMatch(p -> p.message().contains(text)),
        "expected '" + text + "' in " + found);
  }

  private static Model.Field field(String field) {
    return ModelParser.parse(
            entity("").replace("      note: {type: text}\n", "      " + field + "\n"))
        .entities()
        .get("item")
        .field("f")
        .get();
  }

  @Test
  void aFieldCarriesItsBoundsPatternAndDefault() {
    Model.Field number = field("f: {type: number, min: 0, max: 100, default: 5}");
    assertEquals("0", number.min());
    assertEquals("100", number.max());
    assertEquals("5", number.defaultValue());

    Model.Field text = field("f: {type: text, pattern: \"[A-Z]+\", required: true, label: Mã}");
    assertEquals("[A-Z]+", text.pattern());
    assertTrue(text.required());
    assertEquals("Mã", text.label());
  }

  @Test
  void aUniqueFieldHasNoDefaultBecauseEveryRowWouldShareIt() {
    fieldRefused(
        "f: {type: text, unique: true, default: x}", "a unique field cannot have a default");
    assertTrue(field("f: {type: text, unique: true}").unique());
  }

  @Test
  void aChoiceFieldCanHoldSeveralValues() {
    Model.Field field =
        field("f: {type: choice, options: [A, B, C], multiple: true, default: [A, B]}");

    assertTrue(field.multiple());
    assertEquals(List.of("A", "B", "C"), field.options());
  }

  @Test
  void aBadBoundPatternOrDefaultIsRefused() {
    fieldRefused("f: {type: number, min: 10, max: 1}", "min");
    fieldRefused("f: {type: text, pattern: \"[\"}", "pattern");
    fieldRefused("f: {type: choice, options: [A], default: Z}", "default");
    fieldRefused("f: {type: number, default: many}", "default");
    fieldRefused("f: {type: text, multiple: true}", "multiple");
  }

  @Test
  void aFieldCannotBeRenamedFromItself() {
    fieldRefused("f: {type: text, renamed_from: f}", "renamed_from");
  }

  @Test
  void aFileFieldCannotBeUniqueOrBoundedAndAReferenceNeedsAnEntity() {
    fieldRefused("f: {type: file, unique: true}", "unique");
    fieldRefused("f: {type: file, min: 1}", "min");
    fieldRefused("f: {type: reference}", "naming an entity");
  }

  @Test
  void theOwnerOfAChildRecordComesFromAReferenceField() {
    String model =
        "format: 1\nname: x\nroles:\n  - name: A\nentities:\n  parent:\n    fields:\n"
            + "      t: {type: text}\n    access: {A: all}\n  child:\n    fields:\n"
            + "      p: {type: reference, to: parent}\n    access: {A: own}\n    owner_via: p\n";

    assertEquals("p", ModelParser.parse(model).entities().get("child").ownerVia());
    assertThrows(
        ModelException.class,
        () -> ModelParser.parse(model.replace("owner_via: p", "owner_via: ghost")));
    assertThrows(
        ModelException.class,
        () ->
            ModelParser.parse(
                model
                    .replace("owner_via: p", "owner_via: p2")
                    .replace("      p: {type: reference, to: parent}", "      p2: {type: text}")));
  }
}
