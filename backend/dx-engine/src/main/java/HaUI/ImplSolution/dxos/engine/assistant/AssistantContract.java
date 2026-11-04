// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.assistant;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AssistantContract {
  private AssistantContract() {}

  public record HistoryWindow(Instant from, Instant to) {}

  public record Query(@NotBlank @Pattern(regexp = "find|summarize|similar") String operation,
      @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,49}") String model,
      @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,49}") String entity,
      @Size(max = 1000) String query, @Size(max = 20) Map<String, String> filters,
      @Positive Long recordId, HistoryWindow historyWindow, Boolean interpretQuery) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
      throw new IllegalArgumentException("Unknown assistant request field: " + name);
    }
  }

  public record Capabilities(boolean enabled, boolean generationEnabled, List<String> operations,
      int maxCandidates, int maxEvents, int topK) {}

  public record Evidence(String id, String kind, long recordId, Long eventId, String title,
      String content) {}

  public record RecordItem(long id, String title, String status, double score) {}

  public record SummaryItem(String text, List<String> evidenceIds) {}

  public record Coverage(long totalMatching, int candidateCount, boolean complete,
      HistoryWindow historyWindow, boolean historySearched) {}

  public record Result(String status, String reasonCode, String operation, String model,
      String entity, Map<String, String> filters, Coverage coverage, List<RecordItem> records,
      List<Evidence> evidence, List<SummaryItem> summary, String generationStatus, Instant readAt,
      Map<String, Double> timingsMs) {}

  public record TextDocument(String id, String text) {}

  public record RankedItem(String id, double score) {}

  public record RankResult(List<RankedItem> items) {}

  public record PlanResult(String status, String query, Map<String, String> filters) {}

  public record GeneratedResult(String status, List<SummaryItem> summary) {}
}
