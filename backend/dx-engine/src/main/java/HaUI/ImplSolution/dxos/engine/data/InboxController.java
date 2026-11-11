// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inbox")
@Tag(name = "Inbox", description = "The records waiting for the caller, on every applied model")
public class InboxController {

  private static final int MAX_SIZE = 100;

  public record Item(
      String model,
      String entity,
      String entityLabel,
      long id,
      String title,
      String status,
      String assignee,
      boolean overdue,
      Instant createdAt) {}

  private final ModelRegistry registry;
  private final DataService data;

  public InboxController(ModelRegistry registry, DataService data) {
    this.registry = registry;
    this.data = data;
  }

  @GetMapping
  @Operation(
      summary = "The records the caller can act on now, overdue first and then oldest first",
      description =
          "Records in a state the handler role of the caller can act on, within the"
              + " rows it reaches and held by nobody else, and the caller's own records waiting for"
              + " them, on every applied model. page starts at 0 and size is at most "
              + MAX_SIZE
              + ".")
  public ApiResponse<PageResponse<Item>> inbox(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    int limit = Math.max(1, Math.min(size, MAX_SIZE));
    long first = (long) Math.max(0, page) * limit;
    long total = 0;
    for (Model model : registry.all()) {
      for (Entity entity : model.entities().values()) {
        total += data.countWaiting(model, entity);
      }
    }
    List<Item> items = new ArrayList<>();
    // A page past the end needs no rows.
    if (first < total) {
      int upto = (int) Math.min(first + limit, Integer.MAX_VALUE);
      for (Model model : registry.all()) {
        for (Entity entity : model.entities().values()) {
          data.waiting(model, entity, upto).forEach(row -> items.add(item(model, entity, row)));
        }
      }
      items.sort(
          Comparator.comparing((Item item) -> !item.overdue()).thenComparing(Item::createdAt));
    }
    List<Item> shown =
        items.subList(
            (int) Math.min(first, items.size()), (int) Math.min(first + limit, items.size()));
    return ApiResponse.success(
        new PageResponse<>(
            shown, Math.max(0, page), limit, total, (int) ((total + limit - 1) / limit)));
  }

  private static Item item(Model model, Entity entity, Map<String, Object> row) {
    Object title =
        entity.fields().values().stream()
            .filter(field -> field.type() == FieldType.TEXT)
            .findFirst()
            .map(field -> row.get(field.name()))
            .orElse(null);
    return new Item(
        model.name(),
        entity.name(),
        entity.label(),
        ((Number) row.get("id")).longValue(),
        title == null ? null : title.toString(),
        (String) row.get("status"),
        (String) row.get("assignee"),
        Boolean.TRUE.equals(row.get("overdue")),
        (Instant) row.get("created_at"));
  }
}
