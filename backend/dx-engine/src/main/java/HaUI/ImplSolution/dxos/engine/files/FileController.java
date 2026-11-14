// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.files;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/data/{model}/{entity}/{id}/files")
@Tag(name = "Files", description = "Files attached to the records of an applied model")
public class FileController {

  private final ModelRegistry registry;
  private final AttachmentService attachments;

  public FileController(ModelRegistry registry, AttachmentService attachments) {
    this.registry = registry;
    this.attachments = attachments;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Attach a file to a record",
      description =
          "The form carries `field`, a file field of the entity, and `file`. Allowed"
              + " while the caller may change the record and it is not in a final state (403, 409)."
              + " A file the virus scan flags is refused when the scan is on.")
  public ApiResponse<Map<String, Object>> add(
      @PathVariable String model,
      @PathVariable String entity,
      @PathVariable long id,
      @RequestParam String field,
      @RequestParam("file") MultipartFile file) {
    Model m = registry.require(model);
    return ApiResponse.success(attachments.add(m, entity(m, entity), id, field, file));
  }

  @GetMapping
  @Operation(summary = "The files of a record, oldest first")
  public ApiResponse<List<Map<String, Object>>> list(
      @PathVariable String model, @PathVariable String entity, @PathVariable long id) {
    Model m = registry.require(model);
    return ApiResponse.success(attachments.list(m, entity(m, entity), id));
  }

  @GetMapping("/{fileId}/link")
  @Operation(
      summary = "A time-limited link to download a file",
      description = "Allowed to whoever can read the record.")
  public ApiResponse<Map<String, Object>> link(
      @PathVariable String model,
      @PathVariable String entity,
      @PathVariable long id,
      @PathVariable long fileId) {
    Model m = registry.require(model);
    return ApiResponse.success(attachments.link(m, entity(m, entity), id, fileId));
  }

  @DeleteMapping("/{fileId}")
  @Operation(
      summary = "Remove a file from a record",
      description = "Only the user who added the file, while the record is not in a final state.")
  public ApiResponse<Void> remove(
      @PathVariable String model,
      @PathVariable String entity,
      @PathVariable long id,
      @PathVariable long fileId) {
    Model m = registry.require(model);
    attachments.remove(m, entity(m, entity), id, fileId);
    return ApiResponse.success(null);
  }

  private static Entity entity(Model model, String name) {
    return model
        .entity(name)
        .orElseThrow(() -> new ResourceNotFoundException("Entity " + name + " not found"));
  }
}
