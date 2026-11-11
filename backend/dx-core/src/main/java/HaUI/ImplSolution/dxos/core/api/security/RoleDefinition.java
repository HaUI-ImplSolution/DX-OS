// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.security;

// A realm role the running service defines. staff: holders work on records of other users, so they
// see more than their own and are not bound by client restrictions.
public record RoleDefinition(String name,String description,boolean staff){}
