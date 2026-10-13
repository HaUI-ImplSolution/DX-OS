// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// reason: why the case is rejected, shown to the requester
public record WorkflowDecisionRequest(@NotBlank @Size(max=255)String reason){}
