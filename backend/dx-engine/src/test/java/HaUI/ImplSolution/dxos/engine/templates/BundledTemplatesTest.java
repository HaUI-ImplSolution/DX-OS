// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import HaUI.ImplSolution.dxos.engine.apply.DdlGenerator;
import HaUI.ImplSolution.dxos.engine.apply.ModelDiff;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.ModelParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// The models that ship with the system are what a new installation starts from, so each one has
// to be readable, produce its tables, and compare equal to itself.
class BundledTemplatesTest {

  private static List<String> files() throws IOException {
    try (InputStream index =
        BundledTemplatesTest.class.getResourceAsStream("/templates/index.json")) {
      assertNotNull(index, "templates/index.json");
      JsonNode root = new ObjectMapper().readTree(index);
      List<String> files = new ArrayList<>();
      JsonNode list = root.isArray() ? root : root.path("templates");
      list.forEach(item -> files.add(item.path("file").asText(item.asText())));
      return files;
    }
  }

  private static String read(String file) throws IOException {
    try (InputStream in = BundledTemplatesTest.class.getResourceAsStream("/templates/" + file)) {
      assertNotNull(in, file);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void theIndexListsTheTemplatesThatShip() throws IOException {
    List<String> files = files();

    assertTrue(files.size() >= 10, files.toString());
    assertTrue(files.stream().allMatch(f -> f.endsWith(".yaml")), files.toString());
  }

  @Test
  void everyTemplateIsAModelTheReaderAccepts() throws IOException {
    for (String file : files()) {
      Model model = ModelParser.parse(read(file));

      assertFalse(model.entities().isEmpty(), file);
      assertFalse(model.roles().isEmpty(), file);
    }
  }

  @Test
  void everyTemplateMakesItsTables() throws IOException {
    for (String file : files()) {
      Model model = ModelParser.parse(read(file));
      String ddl = DdlGenerator.desiredState(model, Map.of());

      for (String entity : model.entities().keySet()) {
        assertTrue(ddl.contains("CREATE TABLE \"" + entity + "\""), file + " " + entity);
      }
    }
  }

  @Test
  void aTemplateComparedWithItselfChangesNothing() throws IOException {
    for (String file : files()) {
      Model model = ModelParser.parse(read(file));

      ModelDiff diff = ModelDiff.between(model, ModelParser.parse(read(file)), Map.of());

      assertEquals(List.of(), diff.changes, file);
      assertEquals(List.of(), diff.rejections, file);
    }
  }

  @Test
  void aTemplateAppliedToNothingAddsEverything() throws IOException {
    for (String file : files()) {
      Model model = ModelParser.parse(read(file));

      ModelDiff diff = ModelDiff.between(null, model, Map.of());

      assertEquals(
          model.entities().size(),
          diff.changes.stream().filter(c -> c.kind().equals("ADD_ENTITY")).count(),
          file);
      assertEquals(ModelDiff.Level.SAFE, diff.level(), file);
    }
  }
}
