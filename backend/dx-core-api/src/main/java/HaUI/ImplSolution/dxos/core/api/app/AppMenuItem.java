// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.app;

import java.util.Set;

// An entry of the app's navigation. roles: who sees it; empty shows it to every signed-in user.
public record AppMenuItem(String id,String label,String path,Set<String>roles){

public AppMenuItem{roles=roles==null?Set.of():Set.copyOf(roles);}}
