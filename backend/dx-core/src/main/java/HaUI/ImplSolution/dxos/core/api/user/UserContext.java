// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.user;

import java.util.Set;

// The signed-in user of the current request, read from the verified token.
public interface UserContext {

  // Stable account id (token "sub"), unlike the display name which users can change.
  String currentUserId();

  String currentFullName();

  // Realm roles of the signed-in user, without the ROLE_ prefix.
  Set<String> roles();

  boolean hasRole(String role);

  // Roles the signed-in user holds in one Keycloak client, from the token claim "resource_access".
  Set<String> clientRoles(String clientId);

  // Names of the groups the signed-in user belongs to, from the token claim "groups".
  Set<String> groups();

  // Holds one of the roles the running app declares as staff.
  boolean isStaff();
}
