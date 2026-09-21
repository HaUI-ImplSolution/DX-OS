// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// @formatter:off
public record SymptomSuggestionResponse(
  // Disease from the catalog
  @JsonProperty("disease_code") String diseaseCode,
  @JsonProperty("disease_name") String diseaseName,

  // Similarity between the symptom text and the source document; not a diagnostic confidence
  double score,

  // Passage of the document that matched, and the sources it comes from
  String evidence,
  @JsonProperty("source_ids") List<String> sourceIds
) {}
