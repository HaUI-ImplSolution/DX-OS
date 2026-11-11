// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.catalog;

import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// What the control panel reads to help someone write a model: the schema of the model file, the
// process families, the templates, and the versions already applied.
@Service
public class CatalogService {

  private static final String SCHEMA = "schema/model-v0.schema.json";
  private static final String TEMPLATES = "templates/";

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public CatalogService(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public Map<String, Object> schema() {
    return json.readValue(read(SCHEMA), new TypeReference<Map<String, Object>>() {});
  }

  public List<Map<String, Object>> families() {
    List<Map<String, Object>> families = new ArrayList<>();
    for (String name : ProcessFamily.names()) {
      ProcessFamily family = ProcessFamily.find(name).orElseThrow();
      Map<String, Object> described = new LinkedHashMap<>();
      described.put("name", family.name());
      described.put("initial", family.initial());
      described.put("states", family.states());
      described.put("actions", family.actions());
      described.put("params", params(name));
      families.add(described);
    }
    return families;
  }

  public List<Map<String, Object>> templates() {
    List<Map<String, Object>> index =
        json.readValue(read(TEMPLATES + "index.json"), new TypeReference<>() {});
    List<Map<String, Object>> templates = new ArrayList<>();
    for (Map<String, Object> entry : index) {
      Map<String, Object> template = new LinkedHashMap<>(entry);
      template.put("content", read(TEMPLATES + entry.get("file")));
      template.remove("file");
      templates.add(template);
    }
    return templates;
  }

  public List<Map<String, Object>> versions(String model) {
    List<Map<String, Object>> versions = jdbc.queryForList(
        "SELECT version, hash, applied_by, applied_by_name, applied_at FROM engine.model_version "
            + "WHERE model = ? " + "ORDER BY version DESC",
        model);
    if (versions.isEmpty()) {
      throw new ResourceNotFoundException("Model " + model + " not found");
    }
    return versions;
  }

  public Map<String, Object> version(String model, int version) {
    List<Map<String, Object>> rows =
        jdbc.queryForList("SELECT version, hash, applied_by, applied_by_name, applied_at, content "
            + "FROM engine.model_version " + "WHERE model = ? AND version = ?", model, version);
    if (rows.isEmpty()) {
      throw new ResourceNotFoundException("Model " + model + " has no version " + version);
    }
    return rows.get(0);
  }

  private static List<Map<String, Object>> params(String family) {
    List<Map<String, Object>> params = new ArrayList<>();
    params.add(param("handler", true, "The role that handles the records"));
    params
        .add(param("allow_self_decision", false, "Whether the creator of a record may handle it"));
    params.add(param("deadline", false, "How long a handler has, such as 72h, 90m or 3d"));
    if ("work_item".equals(family)) {
      params.add(param("auto_close", false,
          "How long a resolved record waits for its owner before the system closes it, such as 7d"));
    }
    params.add(param("after", false, "Actions taken when a record reaches a final state"));
    if ("reservation".equals(family)) {
      params.add(param("booking", true,
          "The resource field, the start and end fields and the capacity of a resource"));
    }
    return params;
  }

  private static Map<String, Object> param(String name, boolean required, String description) {
    Map<String, Object> param = new LinkedHashMap<>();
    param.put("name", name);
    param.put("required", required);
    param.put("description", description);
    return param;
  }

  private static String read(String path) {
    try (InputStream stream = new ClassPathResource(path).getInputStream()) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ResourceNotFoundException("Cannot read " + path);
    }
  }
}
