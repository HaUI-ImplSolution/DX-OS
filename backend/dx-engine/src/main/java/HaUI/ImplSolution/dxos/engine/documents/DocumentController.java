// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.documents;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data/{model}/{entity}/{id}/documents")
@Tag(name = "Documents", description = "PDF print templates of the records of an applied model")
public class DocumentController {

  private final ModelRegistry registry;
  private final DocumentService documents;

  public DocumentController(ModelRegistry registry, DocumentService documents) {
    this.registry = registry;
    this.documents = documents;
  }

  @GetMapping
  @Operation(
      summary = "The print templates of a record",
      description =
          "Each template with the name of its download and whether the record can be printed in"
              + " its state now. Allowed to whoever can read the record.")
  public ApiResponse<List<Map<String, Object>>> list(
      @PathVariable String model, @PathVariable String entity, @PathVariable long id) {
    Model m = registry.require(model);
    return ApiResponse.success(documents.list(m, entity(m, entity), id));
  }

  @GetMapping("/{name}")
  @Operation(
      summary = "The PDF of a record made from a template",
      description =
          "Allowed to whoever can read the record; 404 for a template the entity does not have and"
              + " 409 when the record is in a state the template excludes.")
  public ResponseEntity<byte[]> print(
      @PathVariable String model,
      @PathVariable String entity,
      @PathVariable long id,
      @PathVariable String name) {
    Model m = registry.require(model);
    DocumentService.Printed printed = documents.print(m, entity(m, entity), id, name);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(printed.file()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(printed.content());
  }

  private static Entity entity(Model model, String name) {
    return model
        .entity(name)
        .orElseThrow(() -> new ResourceNotFoundException("Entity " + name + " not found"));
  }
}
