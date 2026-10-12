// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.notification;

import HaUI.ImplSolution.dxos.core.api.notification.NotificationTypeDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

// The notification types registered by the running app.
@Component
public class NotificationTypes {

  private final Map<String, NotificationTypeDefinition> byCode = new LinkedHashMap<>();

  public NotificationTypes(ObjectProvider<NotificationTypeDefinition> definitions) {
    definitions.orderedStream().forEach(definition -> byCode.put(definition.code(), definition));
  }

  public void require(String code) {
    if (!byCode.containsKey(code)) {
      throw new IllegalArgumentException("Notification type is not registered: " + code);
    }
  }

  public List<NotificationTypeDefinition> all() {
    return List.copyOf(byCode.values());
  }
}
