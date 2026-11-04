// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

// A handler could not reach its target at all (no connection, no answer in time). The dispatcher
// then leaves the other messages of that topic for the next round instead of waiting on each.
public class TargetUnreachableException extends RuntimeException {

  public TargetUnreachableException(String message) {
    super(message);
  }
}
