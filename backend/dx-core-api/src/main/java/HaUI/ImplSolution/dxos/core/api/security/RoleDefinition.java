// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.security;

// A realm role an app defines. staff: holders work on requests of other users, so they see more
// than their own workspace and are not bound by client restrictions.
public record RoleDefinition(String name,String description,boolean staff){}
