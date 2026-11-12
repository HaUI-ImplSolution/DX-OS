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
}
