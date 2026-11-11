// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.InvalidStateException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/outbox")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Outbox", description = "Messages that leave the system after a change; ADMIN only")
public class OutboxController {

  private static final int MAX_SIZE = 100;
  private static final String CONDITION_FAILED = "delivered_at IS NULL AND attempts > 0";
  private static final String CONDITION_PENDING = "delivered_at IS NULL AND attempts = 0";
  private static final String CONDITION_DELIVERED = "delivered_at IS NOT NULL";
  private static final String CONDITION_STOPPED =
      "delivered_at IS NULL AND next_attempt_at = 'infinity'";

  private final JdbcTemplate jdbc;

  public OutboxController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping
  @Operation(
      summary = "The messages of the outbox, newest first, with what is waiting and failing",
      description =
          "status is failed (tried and not delivered, the default), stopped (failed"
              + " messages that used up their tries and wait for a retry), pending (not tried yet) or"
              + " delivered. A failed message shows its last error, whether it stopped, and when it"
              + " is tried next (null when it stopped). counts holds the size of each status. page"
              + " starts at 0 and size is at most "
              + MAX_SIZE
              + ".")
  public ApiResponse<Map<String, Object>> list(
      @RequestParam(defaultValue = "failed") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    String condition =
        switch (status) {
          case "failed" -> CONDITION_FAILED;
          case "stopped" -> CONDITION_STOPPED;
          case "pending" -> CONDITION_PENDING;
          case "delivered" -> CONDITION_DELIVERED;
          default ->
              throw new ModelValidationException(
                  "status", "must be failed, stopped, pending or delivered");
        };
    int limit = Math.max(1, Math.min(size, MAX_SIZE));
    long offset = (long) Math.max(0, page) * limit;
    List<Map<String, Object>> items =
        jdbc.queryForList(
            "SELECT id, topic, model, entity, "
                + "record_id, attempts, last_error, created_at, next_attempt_at = 'infinity' AS stopped, "
                + "CASE WHEN next_attempt_at = 'infinity' THEN NULL ELSE next_attempt_at END "
                + "AS next_attempt_at, delivered_at, payload::text AS payload FROM engine.outbox WHERE "
                + condition
                + " ORDER BY id DESC LIMIT ? OFFSET ?",
            limit,
            offset);
    Map<String, Object> counts =
        jdbc.queryForMap(
            "SELECT count(*) FILTER (WHERE "
                + CONDITION_FAILED
                + ") AS failed, count(*) FILTER "
                + "(WHERE "
                + CONDITION_STOPPED
                + ") AS stopped, count(*) FILTER (WHERE "
                + CONDITION_PENDING
                + ") AS pending, count(*) FILTER (WHERE "
                + CONDITION_DELIVERED
                + ") AS delivered FROM engine.outbox");
    long total = ((Number) counts.get(status)).longValue();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("counts", counts);
    body.put(
        "page",
        new PageResponse<>(
            items, Math.max(0, page), limit, total, (int) ((total + limit - 1) / limit)));
    return ApiResponse.success(body);
  }

  @PostMapping("/{id}/retry")
  @Operation(
      summary = "Try a message that has not been delivered again now",
      description =
          "A message that stopped gets one more try. 404 when there is no such message,"
              + " 409 when it was delivered already.")
  public ApiResponse<Void> retry(@PathVariable long id) {
    int changed =
        jdbc.update(
            "UPDATE engine.outbox SET next_attempt_at = now() WHERE id = ? AND delivered_at IS NULL",
            id);
    if (changed == 0) {
      Integer found =
          jdbc.queryForObject("SELECT count(*) FROM engine.outbox WHERE id = ?", Integer.class, id);
      if (found == null || found == 0) {
        throw new ResourceNotFoundException("Message " + id + " not found");
      }
      throw new InvalidStateException("The message was delivered already");
    }
    return ApiResponse.success(null);
  }
}
