// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.event;

import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import HaUI.ImplSolution.dxos.model.Completion;
import java.util.Map;
import org.springframework.stereotype.Component;

// Carries out the registered operations a model asks for once a record reaches a final state.
@Component
public class CompletionHandler implements OutboxHandler {

  public static final String TOPIC = "completion";

  private final KeycloakAdminClient keycloak;

  public CompletionHandler(KeycloakAdminClient keycloak) {
    this.keycloak = keycloak;
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public void deliver(Map<String, Object> payload) {
    String operation = (String) payload.get("operation");
    if (Completion.ADD_TO_GROUP.equals(operation)) {
      keycloak.addUserToGroup((String) payload.get("user"), (String) payload.get("group"));
      return;
    }
    if (Completion.REMOVE_FROM_GROUP.equals(operation)) {
      keycloak.removeUserFromGroup((String) payload.get("user"), (String) payload.get("group"));
      return;
    }
    throw new IllegalStateException("Unknown operation " + operation);
  }
}
