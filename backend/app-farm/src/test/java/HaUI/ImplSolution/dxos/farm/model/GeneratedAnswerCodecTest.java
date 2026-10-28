// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.model;

import HaUI.ImplSolution.dxos.core.api.ai.GeneratedAnswer;

import static org.assertj.core.api.Assertions.assertThat;

import HaUI.ImplSolution.dxos.farm.dto.AiDiagnoseRequest;
import HaUI.ImplSolution.dxos.farm.dto.AiDiagnoseResponse;
import HaUI.ImplSolution.dxos.farm.dto.KnowledgeAnswerResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GeneratedAnswerCodecTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void internalSnakeCaseBecomesPublicCamelCaseAndPersists() throws Exception {
    String json = """
        {"version":1,"mode":"generated","summary":"Giải thích","steps":[],"note":null,
         "source_ids":["src_1"],"model_name":"qwen2.5:1.5b","prompt_version":"farm_diagnosis-v1"}
        """;
    GeneratedAnswer answer = mapper.readValue(json, GeneratedAnswer.class);
    assertThat(answer.sourceIds()).containsExactly("src_1");
    String saved = mapper.writeValueAsString(answer);
    assertThat(saved).contains("sourceIds", "modelName", "promptVersion")
        .doesNotContain("source_ids");
    assertThat(mapper.readValue(saved, GeneratedAnswer.class)).isEqualTo(answer);
  }

  @Test
  void oldContractsDoNotSerializeOptionalGenerationFields() throws Exception {
    assertThat(mapper.writeValueAsString(new AiDiagnoseRequest("request", 1L, "image", null)))
        .doesNotContain("include_explanation");
    assertThat(
        mapper.writeValueAsString(new AiDiagnoseResponse("request", null, List.of(), null, null)))
        .doesNotContain("generated_explanation");
    assertThat(mapper.writeValueAsString(new KnowledgeAnswerResponse("question", List.of())))
        .doesNotContain("\"answer\"");
  }

  @Test
  void fallbackReasonPersistsAndLegacySnapshotsStillRead() throws Exception {
    GeneratedAnswer answer = GeneratedAnswer.fallback(true, "conflicting_output");
    String saved = mapper.writeValueAsString(answer);
    assertThat(mapper.readValue(saved, GeneratedAnswer.class)).isEqualTo(answer);
    assertThat(answer.summary()).isEmpty();
    GeneratedAnswer old = mapper.readValue("""
        {"version":1,"mode":"extractive","summary":"Legacy fallback", "steps":[],
         "sourceIds":[],"note":null,"modelName":null,"promptVersion":null}
        """, GeneratedAnswer.class);
    assertThat(old.reason()).isNull();
    assertThat(old.mode()).isEqualTo("extractive");
  }
}
