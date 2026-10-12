// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.notification;

import java.util.Map;

// Posts a message to a chat channel's incoming webhook. The app writes the payload; a failed post
// is logged and never reaches the caller.
public interface ChatWebhook {

  // false when the channel has no webhook configured.
  boolean isEnabled(String channelKey);

  void send(String channelKey, Map<String, Object> payload);
}
