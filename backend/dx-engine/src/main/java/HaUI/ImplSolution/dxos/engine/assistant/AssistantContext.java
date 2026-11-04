// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.assistant;

import HaUI.ImplSolution.dxos.core.api.dto.PageResponse;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.Evidence;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.HistoryWindow;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.Query;
import HaUI.ImplSolution.dxos.engine.data.DataService;
import HaUI.ImplSolution.dxos.engine.data.EventLog;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.engine.registry.ModelRegistry;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AssistantContext {
  private final ModelRegistry registry;
  private final DataService data;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate read;

  public AssistantContext(ModelRegistry registry, DataService data, JdbcTemplate jdbc,
      PlatformTransactionManager manager) {
    this.registry = registry;
    this.data = data;
    this.jdbc = jdbc;
    this.read = new TransactionTemplate(manager);
    this.read.setReadOnly(true);
    this.read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    this.read.setTimeout(5);
  }

  public record Snapshot(Model model, Entity entity, long modelVersion, long total,
      List<Map<String, Object>> rows, Map<String, Object> target,
      Map<Long, EventLog.Window> histories, Instant at, String limitReason) {
    boolean sameRecords(Snapshot other) {
      return model.equals(other.model) && modelVersion == other.modelVersion
          && total == other.total && rows.equals(other.rows) && Objects.equals(target, other.target);
    }

    boolean sameSources(Snapshot other) {
      return sameRecords(other) && histories.equals(other.histories);
    }
  }

  public Snapshot capture(Query request, Map<String, String> filters, List<Long> historyIds) {
    return read.execute(status -> {
      Model model = registry.require(request.model());
      Entity entity = model.entity(request.entity())
          .orElseThrow(() -> new ResourceNotFoundException("Entity not found"));
      if (!data.access(entity).read()) {
        throw new AccessDeniedException("Entity cannot be read");
      }
      Long version =
          jdbc.queryForObject("SELECT max(version) FROM engine.model_version WHERE model = ?",
              Long.class, model.name());
      Map<String, String> params = new LinkedHashMap<>();
      filters.forEach((field, value) -> params.put("filter." + field, value));
      params.put("size", String.valueOf(AssistantSettings.CANDIDATES));
      Map<String, Object> target =
          request.recordId() == null ? null : data.get(model, entity, request.recordId());
      PageResponse<Map<String, Object>> page =
          "summarize".equals(request.operation()) ? new PageResponse<>(List.of(target), 0, 1, 1, 1)
              : data.list(model, entity, params);
      Map<Long, EventLog.Window> histories = new LinkedHashMap<>();
      String limit = page.totalElements() > AssistantSettings.CANDIDATES ? "CANDIDATE_LIMIT" : null;
      List<Map<String, Object>> checked = new ArrayList<>(page.items());
      if (target != null) {
        checked.add(target);
      }
      for (Map<String, Object> row : checked) {
        for (Model.Field field : entity.fields().values()) {
          if (field.type() == FieldType.TEXT
              && String.valueOf(row.getOrDefault(field.name(), "")).length() > 4000) {
            limit = "FIELD_LIMIT";
          }
        }
      }
      for (long id : historyIds) {
        HistoryWindow window = request.historyWindow();
        EventLog.Window events =
            data.events(model, entity, id, window == null ? null : window.from(),
                window == null ? null : window.to(), AssistantSettings.EVENTS);
        histories.put(id, events);
        if (events.more()) {
          limit = "HISTORY_LIMIT";
        }
      }
      return new Snapshot(model, entity, version == null ? 0 : version, page.totalElements(),
          page.items(), target, histories, Instant.now(), limit);
    });
  }

  public List<Evidence> evidence(Snapshot snapshot, List<Long> ids) {
    List<Evidence> evidence = new ArrayList<>();
    for (Map<String, Object> row : snapshot.rows()) {
      long id = ((Number) row.get("id")).longValue();
      if (!ids.contains(id)) {
        continue;
      }
      evidence.add(new Evidence("record:" + id, "record", id, null, title(snapshot.entity(), row),
          text(snapshot.entity(), row)));
      EventLog.Window history = snapshot.histories().get(id);
      if (history == null) {
        continue;
      }
      for (Map<String, Object> event : history.items()) {
        long eventId = ((Number) event.get("id")).longValue();
        String content = "Thời điểm: " + event.get("at") + "\nThao tác: "
            + actionLabel(snapshot.entity(), String.valueOf(event.get("action")))
            + "\nNgười thực hiện: " + Objects.toString(event.get("actor_name"), "")
            + "\nTrạng thái: " + stateLabel(snapshot.entity(), event.get("to_status"))
            + "\nGhi chú: " + Objects.toString(event.get("note"), "") + "\nChi tiết: "
            + Objects.toString(event.get("detail"), "");
        evidence.add(new Evidence("event:" + id + ":" + eventId, "event", id, eventId,
            "Lịch sử · " + title(snapshot.entity(), row), content));
      }
    }
    return evidence;
  }

  static String title(Entity entity, Map<String, Object> row) {
    return entity.fields().values().stream().filter(f -> f.type() == FieldType.TEXT)
        .map(f -> Objects.toString(row.get(f.name()), "")).filter(value -> !value.isBlank())
        .findFirst().orElse(entity.label() + " #" + row.get("id"));
  }

  static String text(Entity entity, Map<String, Object> row) {
    StringBuilder text = new StringBuilder(entity.label()).append('\n');
    entity.fields().values().stream()
        .filter(f -> Set.of(FieldType.TEXT, FieldType.CHOICE, FieldType.NUMBER, FieldType.DATETIME)
            .contains(f.type()))
        .filter(f -> row.get(f.name()) != null)
        .forEach(f -> text.append(f.label()).append(": ").append(row.get(f.name())).append('\n'));
    if (entity.process() != null) {
      text.append("Trạng thái: ").append(stateLabel(entity, row.get("status"))).append('\n');
    }
    return text.toString();
  }

  static String stateLabel(Entity entity, Object status) {
    if (entity.process() == null || status == null) {
      return "";
    }
    return entity.process().definition().states().stream()
        .filter(s -> s.name().equals(status.toString())).map(s -> s.label()).findFirst()
        .orElse(status.toString());
  }

  static String actionLabel(Entity entity, String action) {
    if (entity.process() != null) {
      Optional<String> label = entity.process().definition().actions().stream()
          .filter(a -> a.name().equals(action)).map(a -> a.label()).findFirst();
      if (label.isPresent()) {
        return label.get();
      }
    }
    return Map.of("created", "Tạo", "updated", "Sửa", "assigned", "Giao việc", "overdue", "Quá hạn",
        "file_added", "Thêm tệp", "file_removed", "Gỡ tệp").getOrDefault(action, action);
  }

  static void validate(Query query) {
    if (!"find".equals(query.operation()) && query.recordId() == null) {
      throw new ModelValidationException("recordId", "A target record is required");
    }
    if ("summarize".equals(query.operation()) && query.filters() != null
        && !query.filters().isEmpty()) {
      throw new ModelValidationException("filters", "Summary uses the selected record");
    }
    if (query.historyWindow() != null && query.historyWindow().from() != null
        && query.historyWindow().to() != null
        && query.historyWindow().from().isAfter(query.historyWindow().to())) {
      throw new ModelValidationException("historyWindow", "Start must not follow end");
    }
  }
}
