// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workspace;

import java.time.Instant;
import java.util.Map;

// type: a value registered by the running app (see WorkspaceType).
// ownerUserId: token "sub" of the owner member; null while no account is linked.
public record WorkspaceView(Long id,String code,String type,String name,String phone,String address,String ownerUserId,Map<String,String>attributes,Instant createdAt){}
