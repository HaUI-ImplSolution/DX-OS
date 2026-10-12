// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workspace;

import java.util.Map;

// username null creates the workspace without an account. Otherwise the account is created with
// accountRole and becomes the workspace owner.
public record WorkspaceCreateCommand(String type,String name,String phone,String address,Map<String,String>attributes,String username,String email,String accountRole){}
