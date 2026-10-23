// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.audit;

// Keys of the request facts kept in the logging context for the length of a request.
public final class RequestContext {

  public static final String REQUEST_ID = "requestId";
  public static final String CLIENT_IP = "clientIp";
  public static final String METHOD = "httpMethod";
  public static final String PATH = "httpPath";

  private RequestContext() {}
}
