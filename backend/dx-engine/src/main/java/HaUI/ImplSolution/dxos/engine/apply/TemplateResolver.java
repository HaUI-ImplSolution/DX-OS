// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.apply;

import HaUI.ImplSolution.dxos.engine.catalog.CatalogService;
import HaUI.ImplSolution.dxos.model.ModelException;
import HaUI.ImplSolution.dxos.model.ModelException.Problem;
import HaUI.ImplSolution.dxos.model.ModelExtends;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// Turns a model file that names a template in extends into the complete file, so that everything
// after it works on one whole model: the plan, the stored version and the version history. The
// first line of the result records the template and the hash of its text. A file without extends
// is returned as it is.
@Component
public class TemplateResolver {

  // template is the id of the template the file extends, or null when the file does not.
  public record Resolved(String template, String content) {}

  private final CatalogService catalog;

  public TemplateResolver(CatalogService catalog) {
    this.catalog = catalog;
  }

  public Resolved resolve(String content) {
    Optional<String> named = ModelExtends.templateOf(content);
    if (named.isEmpty()) {
      return new Resolved(null, content);
    }
    String id = named.get();
    List<Map<String, Object>> templates = catalog.templates();
    String text =
        templates.stream()
            .filter(template -> id.equals(template.get("id")))
            .map(template -> (String) template.get("content"))
            .findFirst()
            .orElseThrow(() -> unknown(id, templates));
    String merged = ModelExtends.merge(text, content);
    return new Resolved(id, "# extends: " + id + " sha256:" + sha256(text) + "\n" + merged);
  }

  private static ModelException unknown(String id, List<Map<String, Object>> templates) {
    String available =
        templates.stream()
            .map(template -> (String) template.get("id"))
            .collect(Collectors.joining(", "));
    return new ModelException(
        List.of(new Problem(0, 0, "unknown template " + id + ", available: " + available)));
  }

  private static String sha256(String text) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
