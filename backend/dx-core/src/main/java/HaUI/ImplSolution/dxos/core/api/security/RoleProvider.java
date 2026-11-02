// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.security;

import java.util.List;

// The roles of an app. The core creates any that are missing in Keycloak when it starts.
public interface RoleProvider {

  List<RoleDefinition> roles();
}
