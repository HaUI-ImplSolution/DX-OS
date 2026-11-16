// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelUpgraderTest {

  private static final String MODEL =
      """
      format: 0
      name: sample
      roles:
        - name: USER
      entities:
        item:
          fields:
            title: {type: text}
          access: {USER: all}
      """;

  @Test
  void upgradesOneVersionAndPreservesTheModel() {
    ModelUpgrader.Result result = ModelUpgrader.upgrade(MODEL);

    assertEquals(0, result.fromFormat());
    assertEquals(1, result.toFormat());
    assertEquals("format: 1\n" + MODEL.substring("format: 0\n".length()), result.content());
    assertEquals(1, ModelParser.parse(MODEL).format());
  }

  @Test
  void preservesSupplementaryCharactersBeforeTheFormatMarker() {
    String old = "# \uD83D\uDE00\n" + MODEL;

    ModelUpgrader.Result result = ModelUpgrader.upgrade(old);

    assertEquals(
        "# \uD83D\uDE00\nformat: 1\n" + MODEL.substring("format: 0\n".length()), result.content());
  }

  @Test
  void currentFormatIsIdempotent() {
    String current = MODEL.replace("format: 0", "format: 1");

    ModelUpgrader.Result result = ModelUpgrader.upgrade(current);

    assertEquals(current, result.content());
    assertEquals(0, result.migrations().size());
  }

  @Test
  void refusesAFormatFromTheFuture() {
    ModelException error =
        assertThrows(
            ModelException.class,
            () -> ModelUpgrader.upgrade(MODEL.replace("format: 0", "format: 2")));

    assertEquals("unsupported format 2, latest is 1", error.problems().getFirst().message());
  }

  @Test
  void aFailedLaterStepReturnsNoPartialResult() {
    String original = MODEL;
    Map<Integer, ModelUpgrader.Migration> steps =
        Map.of(
            0, (content, node) -> content.replace("format: 0", "format: 1"),
            1,
                (content, node) -> {
                  throw new IllegalStateException("stopped");
                });

    assertThrows(IllegalStateException.class, () -> ModelUpgrader.upgrade(original, 2, steps));
    assertEquals(MODEL, original);
  }

  @Test
  void aiTasksFollowFieldsAndCannotSurviveBrokenReferences() {
    String yaml =
        MODEL
            + """
            ai:
              tasks:
                summarize:
                  input_fields: [title]
                  output_field: title
                  instruction: Summarize the supplied title.
                  sources: [guide_v1]
                  max_length: 100
        """;
    Model parsed = ModelParser.parse(yaml);
    assertEquals("title", parsed.entities().get("item").aiTasks().get("summarize").outputField());
    assertEquals(
        java.util.List.of("title"),
        parsed.entities().get("item").aiTasks().get("summarize").inputFields());
    String unitInput =
        yaml.replace("title: {type: text}", "title: {type: text}\n      unit: {type: unit}")
            .replace("roles:", "units: [{name: TEAM}]\nroles:")
            .replace("input_fields: [title]", "input_fields: [title, unit]");
    assertEquals(
        java.util.List.of("title", "unit"),
        ModelParser.parse(unitInput)
            .entities()
            .get("item")
            .aiTasks()
            .get("summarize")
            .inputFields());
    assertThrows(
        ModelException.class,
        () -> ModelParser.parse(unitInput.replace("output_field: title", "output_field: unit")));
    String valid = yaml;
    assertThrows(
        ModelException.class,
        () -> ModelParser.parse(valid.replace("summarize:", "invalid-task:")));

    assertThrows(
        ModelException.class,
        () -> ModelParser.parse(valid.replace("input_fields: [title]", "input_fields: [missing]")));
    assertThrows(
        ModelException.class,
        () -> ModelParser.parse(valid.replace("output_field: title", "output_field: status")));
    assertThrows(
        ModelException.class,
        () ->
            ModelParser.parse(
                valid.replace("sources: [guide_v1]", "sources: [http://example.invalid]")));
    assertThrows(
        ModelException.class,
        () -> ModelParser.parse(valid.replace("max_length: 100", "max_length: 0")));
  }
}
