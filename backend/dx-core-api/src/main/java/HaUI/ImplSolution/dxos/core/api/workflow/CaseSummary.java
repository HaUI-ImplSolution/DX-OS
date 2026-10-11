// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import java.util.Map;

// What an app tells the approval inbox about the entity behind a case.
// title: business code or short label. workspaceId: the workspace the entity belongs to, or null.
// requesterName: whom the entity belongs to or who raised it. details: values keyed by field name.
public record CaseSummary(String title,Long workspaceId,String requesterName,Map<String,Object>details){

public CaseSummary{details=details==null?Map.of():details;}}
