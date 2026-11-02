// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

// Which rows of an entity a role reaches and what it may do with them.
public enum Scope {
  // Only the rows the user owns; read, create, update and delete.
  OWN,
  // Every row; read, create, update and delete.
  ALL,
  // Every row; read only.
  READ;

  public static Scope of(String key) {
    return switch (key) {
      case "all" -> ALL;
      case "own" -> OWN;
      case "read" -> READ;
      default -> null;
    };
  }

  public String key() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }

  public boolean reachesAll() {
    return this != OWN;
  }

  public boolean canWrite() {
    return this != READ;
  }
}
