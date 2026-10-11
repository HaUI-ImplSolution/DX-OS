// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.ai;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.MultiValueMap;

// Calls to the AI service with the service key attached. A call that fails throws
// AiServiceException; a 4xx answer throws AiRequestRejectedException with the service's detail.
public interface AiGateway {

  // Whether the AI service answers its health check.
  boolean isAvailable();

  <T> T postJson(String path, Object body, Class<T> responseType);

  <T> T postJson(String path, Object body, ParameterizedTypeReference<T> responseType);

  <T> T postMultipart(String path, MultiValueMap<String, Object> parts, Class<T> responseType);
}
