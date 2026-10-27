// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import HaUI.ImplSolution.dxos.farm.model.GeneratedAnswer;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// @formatter:off
public record KnowledgeAnswerResponse(
  String question,

  // Passages of the knowledge base that answer the question, best first; empty when none is
  // relevant enough
  List<Passage> passages,
  @JsonInclude(JsonInclude.Include.NON_NULL) GeneratedAnswer answer
){
  public KnowledgeAnswerResponse(String question, List<Passage> passages) {
    this(question, passages, null);
  }
  public record Passage(
    // Text taken from the document, never generated
    String content,

    String documentTitle,

    String diseaseCode,

    // Similarity to the question, 0 to 1
    double score,

    // Documents the passage is based on
    List<Source> sources
  ){}

  public record Source(
    String sourceId,
    String title,
    String organization,
    String url,
    String license,
    // IN_REVIEW until an expert confirmed the document
    String reviewStatus
  ){}
}
