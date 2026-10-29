// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.security;

import java.util.List;

// Limits on the costly calls of the app, counted per signed-in user.
public interface RateLimitContributor {

  List<RateLimitRule> rateLimits();
}
