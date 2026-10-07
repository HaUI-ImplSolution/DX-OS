// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import java.util.List;

// @formatter:off
public record KnowledgeAnswerResponse(
  String question,

  // Passages of the knowledge base that answer the question, best first; empty when none is
  // relevant enough
  List<Passage> passages
){
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
