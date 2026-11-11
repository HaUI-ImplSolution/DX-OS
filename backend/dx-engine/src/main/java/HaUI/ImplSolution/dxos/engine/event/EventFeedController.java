// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/events")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "Events",
    description =
        "The history lines of records and the changes of administrators as webhook"
            + " messages, pulled by eventId; ADMIN only")
public class EventFeedController {

  private static final int MAX_SIZE = 500;

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public EventFeedController(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @GetMapping
  @Operation(
      summary = "The history lines after an eventId, oldest first, in the layout of the webhook",
      description =
          "after is the eventId the caller holds (0 from the start); size is at most "
              + MAX_SIZE
              + "; model keeps one model. The lines are read from the history itself, so a"
              + " receiver that was down or whose messages stopped catches up whether or not a webhook"
              + " address is set. next is the eventId to ask for after this page; more is true when"
              + " lines are left. The changes of administrators, the apply of a model and the changes"
              + " to accounts, are at /events/admin.")
  public ApiResponse<Map<String, Object>> events(
      @RequestParam(defaultValue = "0") long after,
      @RequestParam(defaultValue = "100") int size,
      @RequestParam(required = false) String model) {
    int limit = limit(after, size);
    String filter = model == null || model.isBlank() ? "" : " AND model = ?";
    Object[] args =
        filter.isEmpty() ? new Object[] {after, limit + 1} : new Object[] {after, model, limit + 1};
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT id, model, entity, record_id, action, from_status, to_status, actor, actor_name, "
                + "at, hash FROM engine.record_event WHERE id > ?"
                + filter
                + " ORDER BY id LIMIT ?",
            args);
    boolean more = rows.size() > limit;
    List<Map<String, Object>> items = new ArrayList<>();
    for (Map<String, Object> row : rows.subList(0, Math.min(rows.size(), limit))) {
      items.add(
          RecordEventPublisher.message(
              ((Number) row.get("id")).longValue(),
              (String) row.get("model"),
              (String) row.get("entity"),
              ((Number) row.get("record_id")).longValue(),
              (String) row.get("action"),
              (String) row.get("from_status"),
              (String) row.get("to_status"),
              (String) row.get("actor"),
              (String) row.get("actor_name"),
              instant(row.get("at")),
              (String) row.get("hash")));
    }
    return ApiResponse.success(page(items, after, more));
  }

  @GetMapping("/admin")
  @Operation(
      summary =
          "The changes of administrators after an eventId, oldest first, in the layout of the"
              + " webhook",
      description =
          "The apply, roll back, resume and abandon of a model and the changes to"
              + " accounts. after is the eventId the caller holds (0 from the start); size is at most "
              + MAX_SIZE
              + "; model keeps one model. The lines are written whether or not a webhook"
              + " address is set. next is the eventId to ask for after this page; more is true when"
              + " lines are left.")
  public ApiResponse<Map<String, Object>> adminEvents(
      @RequestParam(defaultValue = "0") long after,
      @RequestParam(defaultValue = "100") int size,
      @RequestParam(required = false) String model) {
    int limit = limit(after, size);
    String filter = model == null || model.isBlank() ? "" : " AND model = ?";
    Object[] args =
        filter.isEmpty() ? new Object[] {after, limit + 1} : new Object[] {after, model, limit + 1};
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT id, kind, model, actor, actor_name, at, facts::text AS facts "
                + "FROM engine.admin_event WHERE id > ?"
                + filter
                + " ORDER BY id LIMIT ?",
            args);
    boolean more = rows.size() > limit;
    List<Map<String, Object>> items = new ArrayList<>();
    for (Map<String, Object> row : rows.subList(0, Math.min(rows.size(), limit))) {
      items.add(
          AdminEventPublisher.message(
              ((Number) row.get("id")).longValue(),
              (String) row.get("kind"),
              (String) row.get("model"),
              (String) row.get("actor"),
              (String) row.get("actor_name"),
              instant(row.get("at")),
              json.readValue(
                  (String) row.get("facts"), new TypeReference<Map<String, Object>>() {})));
    }
    return ApiResponse.success(page(items, after, more));
  }

  private static int limit(long after, int size) {
    if (after < 0) {
      throw new ModelValidationException("after", "must not be negative");
    }
    return Math.max(1, Math.min(size, MAX_SIZE));
  }

  private static Map<String, Object> page(
      List<Map<String, Object>> items, long after, boolean more) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("items", items);
    body.put("next", items.isEmpty() ? after : items.get(items.size() - 1).get("eventId"));
    body.put("more", more);
    return body;
  }

  static Instant instant(Object at) {
    return at instanceof OffsetDateTime time
        ? time.toInstant()
        : ((java.sql.Timestamp) at).toInstant();
  }
}
