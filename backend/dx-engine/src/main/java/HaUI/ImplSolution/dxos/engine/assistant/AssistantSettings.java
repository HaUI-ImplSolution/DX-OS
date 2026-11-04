// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class AssistantSettings {
  static final int CANDIDATES = 200;
  static final int EVENTS = 100;
  static final int TOP_K = 4;

  private final boolean enabled;
  private final boolean generationEnabled;
  private final String serviceUrl;
  private final String serviceKey;

  public AssistantSettings(@Value("${assistant.enabled:false}") boolean enabled,
      @Value("${assistant.generation-enabled:false}") boolean generationEnabled,
      @Value("${assistant.service-url:}") String serviceUrl,
      @Value("${assistant.service-key:}") String serviceKey) {
    this.enabled = enabled;
    this.generationEnabled = generationEnabled;
    this.serviceUrl = serviceUrl;
    this.serviceKey = serviceKey;
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean generationEnabled() {
    return generationEnabled;
  }

  public String serviceUrl() {
    return serviceUrl;
  }

  public String serviceKey() {
    return serviceKey;
  }
}
