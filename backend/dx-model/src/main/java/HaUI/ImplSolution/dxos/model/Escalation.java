// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// A notice to other roles that a record has stayed unhandled for a time after its deadline passed.
// After is the time past the deadline of the first notice, to the roles that receive it, and
// times the number of notices of this level, spaced by every (one notice when times is 1, every
// null).
public record Escalation(Duration after, List<String> to, Duration every, int times) {

  // One notice of a level: the 1-based level, the 0-based round and the time past the deadline at
  // which it falls due.
  public record Slot(int level, int round, Duration at) {}

  // Every notice the levels can send, in the order they fall due.
  public static List<Slot> slots(List<Escalation> levels) {
    List<Slot> slots = new ArrayList<>();
    for (int i = 0; i < levels.size(); i++) {
      Escalation level = levels.get(i);
      for (int round = 0; round < level.times(); round++) {
        Duration at =
            level.every() == null
                ? level.after()
                : level.after().plus(level.every().multipliedBy(round));
        slots.add(new Slot(i + 1, round, at));
      }
    }
    slots.sort(
        Comparator.comparing(Slot::at).thenComparingInt(Slot::level).thenComparingInt(Slot::round));
    return slots;
  }
}
