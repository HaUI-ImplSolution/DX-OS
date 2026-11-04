// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.registry;

import java.util.Optional;

// The Keycloak client that holds the roles of a model, so two models can use the same role name
// without sharing who holds it.
public final class ModelClient {

  private static final String PREFIX = "dxos-m-";

  private ModelClient() {}

  public static String of(String model) {
    return PREFIX + model;
  }

  // The model a client belongs to, if it is the client of a model.
  public static Optional<String> modelOf(String clientId) {
    return clientId.startsWith(PREFIX) && clientId.length() > PREFIX.length()
        ? Optional.of(clientId.substring(PREFIX.length()))
        : Optional.empty();
  }
}
