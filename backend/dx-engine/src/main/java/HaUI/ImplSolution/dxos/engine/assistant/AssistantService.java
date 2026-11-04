// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.assistant;

import HaUI.ImplSolution.dxos.engine.assistant.AssistantContext.Snapshot;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.Capabilities;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.Coverage;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.Evidence;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.GeneratedResult;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.PlanResult;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.Query;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.RankResult;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.RankedItem;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.RecordItem;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.Result;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.SummaryItem;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.TextDocument;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.model.Model;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AssistantService {
  private final AssistantSettings settings;
  private final AssistantContext context;
  private final AssistantClient client;
  private final ObjectMapper json;

  public AssistantService(AssistantSettings settings, AssistantContext context,
      AssistantClient client, ObjectMapper json) {
    this.settings = settings;
    this.context = context;
    this.client = client;
    this.json = json;
  }

  public Capabilities capabilities() {
    return new Capabilities(settings.enabled(), settings.enabled() && settings.generationEnabled(),
        List.of("find", "summarize", "similar"), AssistantSettings.CANDIDATES,
        AssistantSettings.EVENTS, AssistantSettings.TOP_K);
  }

  public Result query(Query request) {
    AssistantContext.validate(request);
    long started = System.nanoTime();
    long deadline = started + Duration.ofSeconds(60).toNanos();
    long aiDeadline = deadline - Duration.ofSeconds(5).toNanos();
    Map<String, Double> times = new LinkedHashMap<>();
    Map<String, String> filters = validateFilters(request.filters());
    if (!settings.enabled()) {
      return empty(request, filters, null, "UNAVAILABLE", "DISABLED", started, times);
    }
    Snapshot initial = capture(request, filters, List.of(), times);
    String search = Objects.toString(request.query(), "").strip();
    if (Boolean.TRUE.equals(request.interpretQuery()) && "find".equals(request.operation())) {
      if (!settings.generationEnabled() || search.isBlank()) {
        return empty(request, filters, initial, "NEEDS_CLARIFICATION", "QUERY_NOT_INTERPRETED",
            started, times);
      }
      PlanResult plan;
      try {
        plan = call("plan", Map.of("query", search, "fields", fields(initial), "timeoutSeconds",
            remaining(aiDeadline)), PlanResult.class, aiDeadline, times);
      } catch (RuntimeException e) {
        return empty(request, filters, initial, "UNAVAILABLE", "AI_UNAVAILABLE", started, times);
      }
      if (!"SUPPORTED".equals(plan.status()) || plan.filters() == null || plan.query() == null) {
        return empty(request, filters, initial, "NEEDS_CLARIFICATION", "QUERY_NOT_INTERPRETED",
            started, times);
      }
      Map<String, String> proposed;
      try {
        proposed = validateFilters(plan.filters());
      } catch (ModelValidationException e) {
        return empty(request, filters, initial, "NEEDS_CLARIFICATION", "QUERY_NOT_INTERPRETED",
            started, times);
      }
      for (var entry : proposed.entrySet()) {
        if (filters.containsKey(entry.getKey())
            && !filters.get(entry.getKey()).equals(entry.getValue())) {
          return empty(request, filters, initial, "NEEDS_CLARIFICATION", "FILTER_CONFLICT", started,
              times);
        }
        filters.put(entry.getKey(), entry.getValue());
      }
      search = plan.query().strip();
      try {
        initial = capture(request, filters, List.of(), times);
      } catch (ModelValidationException e) {
        return empty(request, filters, null, "NEEDS_CLARIFICATION", "QUERY_NOT_INTERPRETED",
            started, times);
      }
    }
    if (initial.limitReason() != null) {
      return empty(request, filters, initial, "NEEDS_NARROWING", initial.limitReason(), started,
          times);
    }
    List<Long> ids = new ArrayList<>();
    Map<Long, Double> scores = new HashMap<>();
    if ("summarize".equals(request.operation())) {
      ids.add(request.recordId());
    } else {
      List<TextDocument> documents = new ArrayList<>();
      for (var row : initial.rows()) {
        long id = ((Number) row.get("id")).longValue();
        if ("similar".equals(request.operation()) && id == request.recordId()) {
          continue;
        }
        documents
            .add(new TextDocument("record:" + id, AssistantContext.text(initial.entity(), row)));
      }
      if ("similar".equals(request.operation())) {
        search = AssistantContext.text(initial.entity(), initial.target()) + "\n" + search;
      }
      if (search.isBlank()) {
        documents.stream().limit(AssistantSettings.TOP_K)
            .forEach(doc -> ids.add(Long.parseLong(doc.id().substring(7))));
      } else if (!documents.isEmpty()) {
        Object payload = payload(search, documents, aiDeadline);
        if (oversized(payload, documents)) {
          return empty(request, filters, initial, "NEEDS_NARROWING", "CONTEXT_LIMIT", started,
              times);
        }
        RankResult ranked;
        try {
          ranked = call("rank", payload, RankResult.class, aiDeadline, times);
        } catch (RuntimeException e) {
          return empty(request, filters, initial, "UNAVAILABLE", "AI_UNAVAILABLE", started, times);
        }
        Set<String> known = documents.stream().map(TextDocument::id).collect(Collectors.toSet());
        if (ranked.items() == null || ranked.items().size() > AssistantSettings.TOP_K) {
          return empty(request, filters, initial, "UNAVAILABLE", "INVALID_EVIDENCE", started,
              times);
        }
        for (RankedItem item : ranked.items()) {
          if (!known.contains(item.id()) || !Double.isFinite(item.score())) {
            return empty(request, filters, initial, "UNAVAILABLE", "INVALID_EVIDENCE", started,
                times);
          }
          long id = Long.parseLong(item.id().substring(7));
          if (ids.contains(id)) {
            return empty(request, filters, initial, "UNAVAILABLE", "INVALID_EVIDENCE", started,
                times);
          }
          ids.add(id);
          scores.put(id, item.score());
        }
      }
    }
    if (ids.isEmpty()) {
      Snapshot current = recapture(request, filters, List.of(), times);
      if (current == null || !initial.sameRecords(current)) {
        return empty(request, filters, null, "STALE_CONTEXT", "SOURCES_CHANGED", started, times);
      }
      return empty(request, filters, current, "NO_EVIDENCE", "NO_MATCH", started, times);
    }
    Snapshot sources = recapture(request, filters, ids, times);
    if (sources == null || !initial.sameRecords(sources)) {
      return empty(request, filters, null, "STALE_CONTEXT", "SOURCES_CHANGED", started, times);
    }
    if (sources.limitReason() != null) {
      return empty(request, filters, sources, "NEEDS_NARROWING", sources.limitReason(), started,
          times);
    }
    List<Evidence> evidence = context.evidence(sources, ids);
    List<TextDocument> documents =
        evidence.stream().map(e -> new TextDocument(e.id(), e.content())).toList();
    Object payload = payload(Objects.toString(request.query(), ""), documents, aiDeadline);
    if (documents.size() > 200 || oversized(payload, documents)) {
      return empty(request, filters, sources, "NEEDS_NARROWING", "CONTEXT_LIMIT", started, times);
    }
    String generation = "DISABLED";
    List<SummaryItem> summary = List.of();
    boolean filteredList = "find".equals(request.operation()) && search.isBlank();
    if (settings.generationEnabled() && filteredList) {
      generation = "NOT_NEEDED";
    }
    if (settings.generationEnabled() && !filteredList) {
      try {
        GeneratedResult result =
            call("summarize", payload, GeneratedResult.class, aiDeadline, times);
        if ("CONTEXT_LIMIT".equals(result.status())) {
          return empty(request, filters, sources, "NEEDS_NARROWING", "CONTEXT_LIMIT", started,
              times);
        }
        if ("GENERATED".equals(result.status()) && validSummary(result.summary(), evidence)) {
          generation = "GENERATED";
          summary = result.summary();
        } else {
          generation = Set.of("DISABLED", "UNAVAILABLE", "INVALID").contains(result.status())
              ? result.status()
              : "INVALID";
        }
      } catch (RuntimeException e) {
        generation = "UNAVAILABLE";
      }
    }
    Snapshot current = recapture(request, filters, ids, times);
    if (System.nanoTime() >= deadline) {
      return empty(request, filters, null, "UNAVAILABLE", "TIMEOUT", started, times);
    }
    if (current == null || !sources.sameSources(current)) {
      return empty(request, filters, null, "STALE_CONTEXT", "SOURCES_CHANGED", started, times);
    }
    List<RecordItem> records = new ArrayList<>();
    for (long id : ids) {
      var row = sources.rows().stream().filter(r -> ((Number) r.get("id")).longValue() == id)
          .findFirst().orElseThrow();
      records.add(new RecordItem(id, AssistantContext.title(sources.entity(), row),
          AssistantContext.stateLabel(sources.entity(), row.get("status")),
          scores.getOrDefault(id, 0d)));
    }
    return new Result("FOUND", "EVIDENCE_FOUND", request.operation(), request.model(),
        request.entity(), filters, coverage(request, current, true), records, evidence, summary,
        generation, sources.at(), timings(started, times));
  }

  private Snapshot capture(Query request, Map<String, String> filters, List<Long> ids,
      Map<String, Double> times) {
    long started = System.nanoTime();
    try {
      return context.capture(request, filters, ids);
    } finally {
      times.merge("context", (System.nanoTime() - started) / 1_000_000d, Double::sum);
    }
  }

  private <T> T call(String operation, Object payload, Class<T> type, long deadline,
      Map<String, Double> times) {
    long started = System.nanoTime();
    try {
      return client.call(operation, payload, type, deadline);
    } finally {
      times.merge(operation, (System.nanoTime() - started) / 1_000_000d, Double::sum);
    }
  }

  private Snapshot recapture(Query request, Map<String, String> filters, List<Long> ids,
      Map<String, Double> times) {
    try {
      return capture(request, filters, ids, times);
    } catch (RuntimeException e) {
      return null;
    }
  }

  static Map<String, String> validateFilters(Map<String, String> input) {
    Map<String, String> filters = new LinkedHashMap<>();
    if (input == null) {
      return filters;
    }
    if (input.size() > 20) {
      throw new ModelValidationException("filters", "Too many filters");
    }
    input.forEach((raw, value) -> {
      if (raw == null || !raw.matches("[a-z][a-z0-9_]*(\\.(eq|ne|lt|lte|gt|gte|like|in))?")
          || value == null || value.length() > 1000) {
        throw new ModelValidationException("filters", "Invalid field, operator or value");
      }
      String key = raw.contains(".") ? raw : raw + ".eq";
      if (filters.putIfAbsent(key, value) != null) {
        throw new ModelValidationException("filters", "Duplicate filter");
      }
    });
    return filters;
  }

  private List<Map<String, Object>> fields(Snapshot snapshot) {
    List<Map<String, Object>> fields = new ArrayList<>();
    for (Model.Field field : snapshot.entity().fields().values()) {
      if (!Set.of("file", "user", "reference").contains(field.type().key())) {
        fields.add(Map.of("name", field.name(), "label", field.label(), "type", field.type().key(),
            "options", field.options()));
      }
    }
    fields.add(Map.of("name", "created_at", "label", "Ngày tạo", "type", "datetime"));
    if (snapshot.entity().process() != null) {
      fields.add(Map.of("name", "status", "label", "Trạng thái", "type", "choice", "options",
          snapshot.entity().process().definition().states().stream().map(s -> s.name()).toList(),
          "labels", snapshot.entity().process().definition().states().stream()
              .collect(Collectors.toMap(s -> s.name(), s -> s.label()))));
    }
    return fields;
  }

  private static double remaining(long deadline) {
    return Math.max(0.01, Math.min(60, (deadline - System.nanoTime()) / 1_000_000_000d));
  }

  private Object payload(String query, List<TextDocument> documents, long deadline) {
    return Map.of("query", query, "documents", documents, "timeoutSeconds", remaining(deadline));
  }

  private boolean oversized(Object payload, List<TextDocument> documents) {
    return json.writeValueAsBytes(payload).length > 128 * 1024
        || documents.stream().anyMatch(doc -> doc.text().length() > 12000);
  }

  static boolean validSummary(List<SummaryItem> summary, List<Evidence> evidence) {
    if (summary == null || summary.isEmpty() || summary.size() > 4) {
      return false;
    }
    Set<String> known = evidence.stream().map(Evidence::id).collect(Collectors.toSet());
    return summary.stream()
        .allMatch(item -> item != null && item.text() != null && !item.text().isBlank()
            && item.text().length() <= 600 && item.evidenceIds() != null
            && !item.evidenceIds().isEmpty() && item.evidenceIds().size() <= 8
            && known.containsAll(item.evidenceIds()));
  }

  private static Coverage coverage(Query request, Snapshot snapshot, boolean complete) {
    return new Coverage(snapshot == null ? 0 : snapshot.total(),
        snapshot == null ? 0 : snapshot.rows().size(), complete, request.historyWindow(), false);
  }

  private static Map<String, Double> timings(long started, Map<String, Double> times) {
    Map<String, Double> result = new LinkedHashMap<>(times);
    result.put("total", (System.nanoTime() - started) / 1_000_000d);
    return result;
  }

  private static Result empty(Query request, Map<String, String> filters, Snapshot snapshot,
      String status, String reason, long started, Map<String, Double> times) {
    return new Result(status, reason, request.operation(), request.model(), request.entity(),
        filters, coverage(request, snapshot, "NO_EVIDENCE".equals(status)), List.of(), List.of(),
        List.of(), "DISABLED", snapshot == null ? Instant.now() : snapshot.at(),
        timings(started, times));
  }
}
