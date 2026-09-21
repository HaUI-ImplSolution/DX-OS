// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.exception;

// Keycloak Admin API could not complete a request (unreachable, rejected or unexpected reply).
public class KeycloakAdminException extends RuntimeException {

  public KeycloakAdminException(String message) {
    super(message);
  }

  public KeycloakAdminException(String message, Throwable cause) {
    super(message, cause);
  }
}
