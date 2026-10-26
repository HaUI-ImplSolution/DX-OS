// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.web;

import HaUI.ImplSolution.dxos.monitor.config.MonitorProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class MonitorPageController {

  private final String signOutUrl;

  public MonitorPageController(MonitorProperties properties,
      @Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId,
      @Value("${monitor.keycloak-logout-url}") String logoutUrl) {
    this.signOutUrl = logoutUrl + "?client_id=" + encode(clientId) + "&post_logout_redirect_uri="
        + encode(properties.publicUrl() + "/monitor/");
  }

  @GetMapping("/")
  public String page(@AuthenticationPrincipal OidcUser user, Model model) {
    model.addAttribute("username", user.getName());
    return "monitor";
  }

  // Shown to an account that signed in but is not an administrator.
  @GetMapping("/forbidden")
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public String forbidden(Model model) {
    model.addAttribute("signOutUrl", signOutUrl);
    return "forbidden";
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
