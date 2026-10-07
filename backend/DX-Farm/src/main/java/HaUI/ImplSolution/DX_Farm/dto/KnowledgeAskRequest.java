// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// @formatter:off
public record KnowledgeAskRequest(
  @NotBlank @Size(max = 1000)
  String question,

  // Optional: limit the answer to one disease of the catalogue
  @Size(max = 100)
  String diseaseCode
){}
