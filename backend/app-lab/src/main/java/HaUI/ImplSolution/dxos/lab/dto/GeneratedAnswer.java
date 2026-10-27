// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Set;

// Generated wording is separate from verbatim knowledge passages.
// @formatter:off
public record GeneratedAnswer(
    int version,
    String mode,
    String summary,
    List<String> steps,
    String note,
    @JsonAlias("source_ids") List<String> sourceIds,
    @JsonAlias("model_name") String modelName,
    @JsonAlias("prompt_version") String promptVersion,
    String reason
) {
  public GeneratedAnswer {
    if (version != 1 || mode == null
        || !Set.of("generated", "extractive", "insufficient_context").contains(mode)
        || summary == null || summary.length() > 350
        || reason != null && !Set.of("insufficient_evidence", "out_of_scope",
            "requires_authority", "generation_failed", "conflicting_output").contains(reason)
        || steps == null || steps.size() > 3
        || steps.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 180)
        || note != null && note.length() > 220
        || sourceIds == null || sourceIds.size() > 16
        || sourceIds.stream().anyMatch(s -> s == null || !s.matches("[A-Za-z0-9_.:-]{1,128}"))) {
      throw new IllegalArgumentException("Invalid generated answer");
    }
    steps = List.copyOf(steps);
    sourceIds = List.copyOf(sourceIds);
    if (mode.equals("generated") && (summary.isBlank() || reason != null || sourceIds.isEmpty() || modelName == null
        || modelName.isBlank() || promptVersion == null || promptVersion.isBlank())) {
      throw new IllegalArgumentException("Generated answers require sources and model metadata");
    }
  }

  public GeneratedAnswer(int version, String mode, String summary, List<String> steps,
      String note, List<String> sourceIds, String modelName, String promptVersion) {
    this(version, mode, summary, steps, note, sourceIds, modelName, promptVersion, null);
  }

  public boolean supportedBy(Set<String> allowed) {
    return allowed.containsAll(sourceIds);
  }

  public static GeneratedAnswer fallback(boolean hasEvidence) {
    return fallback(hasEvidence, hasEvidence ? "generation_failed" : "insufficient_evidence");
  }

  public static GeneratedAnswer fallback(boolean hasEvidence, String reason) {
    return new GeneratedAnswer(1, hasEvidence ? "extractive" : "insufficient_context",
        "", List.of(), null, List.of(), null, null, reason);
  }
}
