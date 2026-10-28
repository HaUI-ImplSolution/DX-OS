// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import HaUI.ImplSolution.dxos.core.api.ai.AiGateway;
import HaUI.ImplSolution.dxos.core.api.exception.AiServiceException;
import HaUI.ImplSolution.dxos.core.api.knowledge.*;
import HaUI.ImplSolution.dxos.core.api.ai.GeneratedAnswer;
import HaUI.ImplSolution.dxos.lab.dto.KnowledgeDtos.AskRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeServiceAnswerTest {
  private final KnowledgeGateway knowledge = mock(KnowledgeGateway.class);
  private final AiGateway ai = mock(AiGateway.class);
  private final KnowledgeService service = new KnowledgeService(knowledge, ai);

  private void evidence() {
    var source = new KnowledgeSource("src_1", "Nguồn", null, null, null, null, null);
    var passage = new KnowledgePassage("Nội dung gốc", "Tài liệu", 0.8, Map.of(), List.of(source));
    when(knowledge.ask(eq("lab"), any(), any(), eq(4)))
        .thenReturn(new KnowledgeAnswer("Câu hỏi", List.of(passage)));
  }

  @Test
  void oldRequestKeepsEvidenceWithoutCallingGeneration() {
    evidence();
    var result = service.ask(new AskRequest("Quy định gì?"));
    assertThat(result.answer()).isNull();
    assertThat(result.passages().getFirst().content()).isEqualTo("Nội dung gốc");
    verifyNoInteractions(ai);
  }

  @Test
  void generatedAnswerKeepsOriginalPassages() {
    evidence();
    var generated = new GeneratedAnswer(1, "generated", "Câu trả lời", List.of(), null,
        List.of("src_1"), "qwen2.5:1.5b", "lab_qa-v2");
    when(ai.postJson(eq("/api/v1/knowledge/answer"), any(), eq(GeneratedAnswer.class)))
        .thenReturn(generated);
    var result = service.ask(new AskRequest("Quy định gì?"), true);
    assertThat(result.answer()).isEqualTo(generated);
    assertThat(result.passages().getFirst().content()).isEqualTo("Nội dung gốc");
  }

  @Test
  void unrecognizedSourceAndUnavailableModelFallBack() {
    evidence();
    var generated = new GeneratedAnswer(1, "generated", "Câu trả lời", List.of(), null,
        List.of("invented"), "qwen2.5:1.5b", "lab_qa-v2");
    when(ai.postJson(eq("/api/v1/knowledge/answer"), any(), eq(GeneratedAnswer.class)))
        .thenReturn(generated).thenThrow(new AiServiceException("unavailable", null));
    assertThat(service.ask(new AskRequest("Quy định gì?"), true).answer().mode())
        .isEqualTo("extractive");
    assertThat(service.ask(new AskRequest("Quy định gì?"), true).answer().mode())
        .isEqualTo("extractive");
  }

  @Test
  void noEvidenceDoesNotCallModel() {
    when(knowledge.ask(eq("lab"), any(), any(), eq(4)))
        .thenReturn(new KnowledgeAnswer("Câu hỏi", List.of()));
    assertThat(service.ask(new AskRequest("Quy định gì?"), true).answer().mode())
        .isEqualTo("insufficient_context");
    verifyNoInteractions(ai);
  }

  @Test
  void oldAiEndpointFallsBackButAuthorizationErrorsArePreserved() {
    evidence();
    when(ai.postJson(eq("/api/v1/knowledge/answer"), any(), eq(GeneratedAnswer.class)))
        .thenThrow(new HaUI.ImplSolution.dxos.core.api.exception.AiRequestRejectedException(404,
            "missing", null))
        .thenThrow(new HaUI.ImplSolution.dxos.core.api.exception.AiRequestRejectedException(403,
            "denied", null));
    var fallback = service.ask(new AskRequest("Quy định gì?"), true).answer();
    assertThat(fallback.summary()).isEmpty();
    assertThat(fallback.reason()).isEqualTo("generation_failed");
    assertThatThrownBy(() -> service.ask(new AskRequest("Quy định gì?"), true))
        .isInstanceOf(HaUI.ImplSolution.dxos.core.api.exception.AiRequestRejectedException.class);
  }

  @Test
  void reasonSurvivesWhileGeneratedAnswersFromOldPromptAreRejected() {
    evidence();
    var refused = GeneratedAnswer.fallback(true, "requires_authority");
    var old = new GeneratedAnswer(1, "generated", "Câu trả lời", List.of(), null, List.of("src_1"),
        "qwen2.5:1.5b", "lab_qa-v1");
    when(ai.postJson(eq("/api/v1/knowledge/answer"), any(), eq(GeneratedAnswer.class)))
        .thenReturn(refused).thenReturn(old);
    assertThat(service.ask(new AskRequest("Quy định gì?"), true).answer()).isEqualTo(refused);
    assertThat(service.ask(new AskRequest("Quy định gì?"), true).answer().reason())
        .isEqualTo("conflicting_output");
  }
}
