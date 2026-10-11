// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.security;

import HaUI.ImplSolution.dxos.core.api.security.RoleDefinition;
import HaUI.ImplSolution.dxos.core.exception.KeycloakAdminException;
import HaUI.ImplSolution.dxos.core.identity.KeycloakAdminClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

// Creates the realm roles of the running app that Keycloak does not have yet. A role that exists
// is left alone. Keycloak usually starts after the backend, so roles that could not be reached are
// retried in the background until they exist or the attempts run out. A refusal from Keycloak
// (the service account may not create roles) is logged once and not retried. A failure never
// stops the backend.
@Component
public class RoleBootstrap {

  private static final Logger log = LoggerFactory.getLogger(RoleBootstrap.class);

  private final AppRoles appRoles;
  private final KeycloakAdminClient keycloakAdminClient;
  private final int maxAttempts;
  private final long retryDelaySeconds;

  public RoleBootstrap(AppRoles appRoles, KeycloakAdminClient keycloakAdminClient,
      @Value("${keycloak.admin.role-bootstrap-attempts:40}") int maxAttempts,
      @Value("${keycloak.admin.role-bootstrap-delay-seconds:15}") long retryDelaySeconds) {
    this.appRoles = appRoles;
    this.keycloakAdminClient = keycloakAdminClient;
    this.maxAttempts = maxAttempts;
    this.retryDelaySeconds = retryDelaySeconds;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ensureRoles() {
    List<RoleDefinition> roles = appRoles.all();
    if (roles.isEmpty()) {
      return;
    }
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
      Thread thread = new Thread(task, "role-bootstrap");
      thread.setDaemon(true);
      return thread;
    });
    attempt(executor, roles, 1);
  }

  private void attempt(ScheduledExecutorService executor, List<RoleDefinition> roles, int attempt) {
    List<RoleDefinition> missing = new ArrayList<>();
    for (RoleDefinition role : roles) {
      try {
        keycloakAdminClient.ensureRealmRole(role.name(), role.description());
      } catch (KeycloakAdminException e) {
        if (e.getCause() instanceof RestClientResponseException) {
          log.warn("Keycloak refused to create the realm role {}: {}", role.name(), e.getMessage());
        } else {
          missing.add(role);
        }
      }
    }
    if (missing.isEmpty()) {
      executor.shutdown();
      return;
    }
    if (attempt >= maxAttempts) {
      log.warn("Could not make sure the realm roles {} exist after {} attempts",
          missing.stream().map(RoleDefinition::name).toList(), attempt);
      executor.shutdown();
      return;
    }
    executor.schedule(() -> attempt(executor, missing, attempt + 1), retryDelaySeconds,
        TimeUnit.SECONDS);
  }
}
