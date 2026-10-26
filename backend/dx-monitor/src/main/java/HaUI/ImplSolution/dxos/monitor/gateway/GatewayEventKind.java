// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.gateway;

// Why a gateway request is worth keeping.
public enum GatewayEventKind {
  RATE_LIMITED, AUTH_REJECTED, NOT_FOUND, SERVER_ERROR, UPSTREAM_DOWN, SLOW
}
