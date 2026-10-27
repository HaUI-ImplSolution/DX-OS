// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.dto;

import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeSource;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

// Question answering over the Lab knowledge base.
public final class KnowledgeDtos {

  public record AskRequest(@NotBlank @Size(max = 1000) String question) {}

  public record Source(String sourceId, String title, String organization, String url,
      String documentUrl, String license, String reviewStatus) {

    public static Source of(KnowledgeSource source) {
      return new Source(source.sourceId(), source.title(), source.organization(), source.url(),
          source.documentUrl(), source.license(), source.reviewStatus());
    }
  }

  public record Passage(String content, String documentTitle, double score, List<Source> sources) {}

  public record AnswerResponse(String question, List<Passage> passages,
      @JsonInclude(JsonInclude.Include.NON_NULL) GeneratedAnswer answer) {
    public AnswerResponse(String question, List<Passage> passages) {
      this(question, passages, null);
    }
  }

  private KnowledgeDtos() {}
}
