package HaUI.ImplSolution.DX_Farm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// @formatter:off
public record SymptomSuggestionResponse(
  @JsonProperty("disease_code") String diseaseCode,
  @JsonProperty("disease_name") String diseaseName,
  double score,
  String evidence,
  @JsonProperty("source_ids") List<String> sourceIds
) {}
