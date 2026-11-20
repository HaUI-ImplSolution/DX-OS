// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// The address of a record on the web app, which the API and the webhook carry so that another
// application can send a person there. There is none when the stack runs without the web app or
// has no public address.
@Component
public class WebLinks {

  private final String base;

  public WebLinks(
      @Value("${engine.web.url:}") String url, @Value("${engine.web.mode:full}") String mode) {
    String trimmed = url.strip();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    this.base = "core".equals(mode.strip()) ? "" : trimmed;
  }

  // The page of one record, or null when there is no web app to show it.
  public String record(String model, String entity, long id) {
    return base.isEmpty() ? null : base + "/" + model + "-" + entity + "/" + id + "/show";
  }
}
