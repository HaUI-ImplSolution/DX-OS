// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.security;

import java.util.List;

// Opens parts of the app's API. Everything not listed needs a signed-in user.
public interface EndpointPolicyContributor {

  // Ant patterns that answer GET without a token.
  List<String> publicGetPaths();
}
