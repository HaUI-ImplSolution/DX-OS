// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

// Which rows of an entity a role reaches and what it may do with them.
public enum Scope {
  // Only the rows the user owns; read, create, update and delete.
  OWN,
  // Every row; read, create, update and delete.
  ALL,
  // Every row; read only.
  READ,
  // The rows of the units the user belongs to; read, create, update and delete.
  UNIT,
  // The rows assigned to the user; read, update and delete.
  ASSIGNED;

  public static Scope of(String key) {
    return switch (key) {
      case "all" -> ALL;
      case "own" -> OWN;
      case "read" -> READ;
      case "unit" -> UNIT;
      case "assigned" -> ASSIGNED;
      default -> null;
    };
  }

  public String key() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }

  public boolean reachesAll() {
    return this == ALL || this == READ;
  }

  public boolean canWrite() {
    return this != READ;
  }
}
