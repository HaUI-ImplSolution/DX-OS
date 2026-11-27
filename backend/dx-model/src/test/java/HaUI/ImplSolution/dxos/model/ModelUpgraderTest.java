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
  void aiTasksAreReadAndRefuseAFieldThatDoesNotExist() {
    String yaml =
        MODEL.replace("access:", "  kind: {type: choice, options: [A, B]}\n    access:")
            + """
            ai:
              tasks:
                suggest:
                  classifier: kinds
                  input_fields: [title]
                  output_field: kind
        """;
    var task = ModelParser.parse(yaml).entities().get("item").aiTasks().get("suggest");
    assertEquals("kind", task.outputField());
    assertEquals(java.util.List.of("title"), task.inputFields());
    assertThrows(
        ModelException.class,
        () -> ModelParser.parse(yaml.replace("input_fields: [title]", "input_fields: [missing]")));
  }
}
