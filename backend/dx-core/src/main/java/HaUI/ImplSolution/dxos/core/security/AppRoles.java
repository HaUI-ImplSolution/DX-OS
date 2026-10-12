// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import HaUI.ImplSolution.dxos.core.api.security.RoleDefinition;
import HaUI.ImplSolution.dxos.core.api.security.RoleProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

// The roles the running app defines.
@Component
public class AppRoles {

  private final Map<String, RoleDefinition> byName = new LinkedHashMap<>();

  public AppRoles(ObjectProvider<RoleProvider> providers) {
    providers.orderedStream().flatMap(provider -> provider.roles().stream())
        .forEach(role -> byName.put(role.name(), role));
  }

  public List<RoleDefinition> all() {
    return List.copyOf(byName.values());
  }

  public Set<String> staffRoles() {
    return byName.values().stream().filter(RoleDefinition::staff).map(RoleDefinition::name)
        .collect(Collectors.toUnmodifiableSet());
  }
}
