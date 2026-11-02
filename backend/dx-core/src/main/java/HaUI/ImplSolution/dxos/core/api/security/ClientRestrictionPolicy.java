// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.security;

import java.util.Set;

// Limits which Keycloak clients may use an account that holds only the given role: a token that
// carries the role and no staff role is accepted only when it was issued to an allowed client
// (the "azp" claim).
public interface ClientRestrictionPolicy {

  String role();

  Set<String> allowedClientIds();
}
