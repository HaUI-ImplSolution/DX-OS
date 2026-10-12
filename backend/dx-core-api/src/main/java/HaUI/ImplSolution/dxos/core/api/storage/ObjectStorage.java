// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.storage;

import java.time.Duration;
import java.util.Map;

// Private object store shared by the applications.
public interface ObjectStorage {

  void putObject(String key, byte[] data, String contentType, Map<String, String> metadata);

  // Time-limited link for the origin the current request was served from.
  String presignedUrl(String key);

  // Time-limited link for a fixed public origin, for messages sent outside any request.
  String presignedUrl(String baseUrl, String key, Duration ttl);

  // Time-limited link for services inside the Docker network.
  String internalPresignedUrl(String key, Duration ttl);

  // True when the bucket is reachable.
  boolean isReady();
}
