// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.engine.assistant.AssistantContext.Snapshot;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.Evidence;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.GeneratedResult;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.PlanResult;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.Query;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.RankResult;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.RankedItem;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.Result;
import HaUI.ImplSolution.dxos.engine.assistant.AssistantContract.SummaryItem;
import HaUI.ImplSolution.dxos.engine.exception.ModelValidationException;
import HaUI.ImplSolution.dxos.model.FieldType;
import HaUI.ImplSolution.dxos.model.Model;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

class AssistantServiceTest {
  private final AssistantContext context = mock(AssistantContext.class);
  private final AssistantClient client = mock(AssistantClient.class);

  private AssistantService service(boolean enabled, boolean generation) {
    return new AssistantService(
        new AssistantSettings(enabled, generation, "http://ai:8000", "test"), context, client,
        JsonMapper.builder().build());
  }

  private Query query(boolean interpret) {
    return new Query("find", "example", "request", "printer", Map.of(), null, null, interpret);
  }

  private Snapshot snapshot(String title, long total) {
    Model.Field field = new Model.Field("title", FieldType.TEXT, "Tiêu đề", true, false, List.of(),
        null, null, null);
    Model.Entity entity =
        new Model.Entity("request", "Yêu cầu", Map.of("title", field), Map.of(), null, null);
    Model model = new Model(0, "example", List.of(), List.of(), Map.of("request", entity));
    return new Snapshot(model, entity, 1, total,
        List.of(Map.of("id", 1L, "title", title, "version", 1L)), null, Map.of(),
        Instant.parse("2026-11-03T00:00:00Z"), total > 200 ? "CANDIDATE_LIMIT" : null);
  }

  @Test
  void requestAcceptsOmittedOptionalFields() {
    Query parsed = JsonMapper.builder().build().readValue(
        "{\"operation\":\"find\",\"model\":\"example\",\"entity\":\"request\",\"query\":\"printer\"}",
        Query.class);
    assertEquals("find", parsed.operation());
  }

  @Test
  void disabledDoesNotReadBusinessDataOrCallAi() {
    assertEquals("UNAVAILABLE", service(false, false).query(query(false)).status());
    verifyNoInteractions(context, client);
  }

  @Test
  void permissionDenialPrecedesAi() {
    when(context.capture(any(), anyMap(), anyList()))
        .thenThrow(new AccessDeniedException("denied"));
    assertThrows(AccessDeniedException.class, () -> service(true, false).query(query(false)));
    verifyNoInteractions(client);
  }

  @Test
  void oversizedCandidateSetIsNotSilentlySampled() {
    when(context.capture(any(), anyMap(), anyList())).thenReturn(snapshot("printer", 201));
    Result result = service(true, false).query(query(false));
    assertEquals("NEEDS_NARROWING", result.status());
    assertEquals(201, result.coverage().totalMatching());
    assertFalse(result.coverage().complete());
    verifyNoInteractions(client);
  }

  @Test
  void unknownCandidateFromAiIsRejected() {
    when(context.capture(any(), anyMap(), anyList())).thenReturn(snapshot("printer", 1));
    when(client.call(eq("rank"), any(), eq(RankResult.class), anyLong()))
        .thenReturn(new RankResult(List.of(new RankedItem("record:999", 0.9))));
    assertEquals("INVALID_EVIDENCE", service(true, false).query(query(false)).reasonCode());
  }

  @Test
  void sourceChangeDiscardsGeneratedTextAndEvidence() {
    Snapshot original = snapshot("printer", 1);
    when(context.capture(any(), anyMap(), anyList())).thenReturn(original, original,
        snapshot("changed", 1));
    when(client.call(eq("rank"), any(), eq(RankResult.class), anyLong()))
        .thenReturn(new RankResult(List.of(new RankedItem("record:1", 0.9))));
    when(client.call(eq("summarize"), any(), eq(GeneratedResult.class), anyLong()))
        .thenReturn(new GeneratedResult("GENERATED",
            List.of(new SummaryItem("Máy in chưa hoạt động.", List.of("record:1")))));
    Result result = service(true, true).query(query(false));
    assertEquals("STALE_CONTEXT", result.status());
    assertTrue(result.summary().isEmpty());
    assertTrue(result.evidence().isEmpty());
  }

  @Test
  void filteringConflictCannotReplaceUserConstraint() {
    when(context.capture(any(), anyMap(), anyList())).thenReturn(snapshot("printer", 1));
    when(client.call(eq("plan"), any(), eq(PlanResult.class), anyLong()))
        .thenReturn(new PlanResult("SUPPORTED", "printer", Map.of("title.eq", "other")));
    Query request = new Query("find", "example", "request", "printer", Map.of("title", "chosen"),
        null, null, true);
    Result result = service(true, true).query(request);
    assertEquals("FILTER_CONFLICT", result.reasonCode());
    assertEquals("chosen", result.filters().get("title.eq"));
    verify(client, never()).call(eq("rank"), any(), any(), anyLong());
  }

  @Test
  void summaryCannotCiteUnknownSources() {
    Evidence known = new Evidence("record:1", "record", 1, null, "Source", "content");
    assertFalse(AssistantService
        .validSummary(List.of(new SummaryItem("claim", List.of("record:2"))), List.of(known)));
    assertTrue(AssistantService.validSummary(List.of(new SummaryItem("claim", List.of("record:1"))),
        List.of(known)));
  }

  @Test
  void invalidOperatorsAndDuplicateNormalizedFiltersAreRejected() {
    assertThrows(ModelValidationException.class,
        () -> AssistantService.validateFilters(Map.of("title.or", "x")));
    assertThrows(ModelValidationException.class,
        () -> AssistantService.validateFilters(Map.of("title", "x", "title.eq", "y")));
  }

  @Test
  void filterOnlyResultsUseBackendCountsWithoutGeneratingAClaimOverTopK() {
    when(context.capture(any(), anyMap(), anyList())).thenReturn(snapshot("printer", 50));
    Query filtered = new Query("find", "example", "request", "", Map.of("title.like", "printer"),
        null, null, false);
    Result result = service(true, true).query(filtered);
    assertEquals("FOUND", result.status());
    assertEquals(50, result.coverage().totalMatching());
    assertEquals("NOT_NEEDED", result.generationStatus());
    assertTrue(result.summary().isEmpty());
    verifyNoInteractions(client);
  }
}
