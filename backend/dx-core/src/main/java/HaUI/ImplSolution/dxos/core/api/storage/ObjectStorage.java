// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.storage;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

// Private object store shared by the applications.
public interface ObjectStorage {

  void putObject(String key, byte[] data, String contentType, Map<String, String> metadata);

  // Time-limited link for the origin the current request was served from.
  String presignedUrl(String key);

  // Like presignedUrl, but the browser saves the file under this name instead of showing it.
  String presignedDownloadUrl(String key, String filename);

  // The content of the object, or empty when there is no such object.
  Optional<byte[]> getObject(String key);

  // Removes the object; one that is not there is not an error.
  void deleteObject(String key);

  // Time-limited link for a fixed public origin, for messages sent outside any request.
  String presignedUrl(String baseUrl, String key, Duration ttl);

  // Time-limited link for services inside the Docker network.
  String internalPresignedUrl(String key, Duration ttl);

  // True when the bucket is reachable.
  boolean isReady();
}
