// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.util.Set;

// An action the system takes once a record reaches a final state: one of the registered
// operations, with where its arguments come from. "user" is created_by or a user field; the group
// is a fixed name or the value of a field.
public record Completion(String operation, String user, String group, String groupField) {

  public static final String ADD_TO_GROUP = "add_to_group";
  public static final String REMOVE_FROM_GROUP = "remove_from_group";
  public static final String CREATOR = "created_by";
  public static final Set<String> OPERATIONS = Set.of(ADD_TO_GROUP, REMOVE_FROM_GROUP);
}
