// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import java.util.Map;

// Takes the messages of one topic. A failure is retried, so delivering twice must be harmless.
public interface OutboxHandler {

  String topic();

  void deliver(Map<String, Object> payload);
}
