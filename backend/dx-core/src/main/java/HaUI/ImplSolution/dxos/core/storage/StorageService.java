// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.storage;

import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Object storage on an S3-compatible server. Objects are private; clients and other services read
// them through time-limited presigned URLs.
@Service
public class StorageService implements ObjectStorage {

  // Fixed so signing a URL is pure computation and never asks the server for the bucket region.
  private static final String REGION = "us-east-1";
  private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]+(:\\d{1,5})?");
  private static final int MAX_CACHED_SIGNERS = 32;

  private final MinioClient client;
  private final String bucket;
  private final String endpoint;
  private final String accessKey;
  private final String secretKey;
  private final String publicUrl;
  private final Duration presignTtl;
  private final Map<String, MinioClient> signers = new ConcurrentHashMap<>();

  public StorageService(@Value("${storage.endpoint}") String endpoint,
      @Value("${storage.access-key}") String accessKey,
      @Value("${storage.secret-key}") String secretKey, @Value("${storage.bucket}") String bucket,
      @Value("${storage.public-url}") String publicUrl,
      @Value("${storage.presign-ttl}") Duration presignTtl) {
    this.endpoint = trimTrailingSlash(endpoint);
    this.accessKey = accessKey;
    this.secretKey = secretKey;
    this.bucket = bucket;
    this.publicUrl = trimTrailingSlash(publicUrl);
    this.presignTtl = presignTtl;
    this.client = signer(this.endpoint);
  }

  public String bucket() {
    return bucket;
  }

  public MinioClient client() {
    return client;
  }

  @Override
  public void putObject(String key, byte[] data, String contentType, Map<String, String> metadata) {
    try {
      client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
          .stream(new ByteArrayInputStream(data), data.length, -1).contentType(contentType)
          .userMetadata(metadata).build());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to store object " + key, e);
    }
  }

  // Link a browser or phone can load, valid for the configured time. It is signed for the host the
  // caller used to reach the API, so it keeps working behind any address of the gateway.
  @Override
  public String presignedUrl(String key) {
    return presign(requestBaseUrl(), key, presignTtl);
  }

  // Link for a fixed public origin, for messages sent outside any request (e.g. chat).
  @Override
  public String presignedUrl(String baseUrl, String key, Duration ttl) {
    return presign(trimTrailingSlash(baseUrl), key, ttl);
  }

  // Link for services inside the compose network (the AI service).
  @Override
  public String internalPresignedUrl(String key, Duration ttl) {
    return presign(endpoint, key, ttl);
  }

  @Override
  public boolean isReady() {
    try {
      return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    } catch (Exception e) {
      return false;
    }
  }

  private String presign(String baseUrl, String key, Duration ttl) {
    try {
      return signer(baseUrl).getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
          .method(Method.GET).bucket(bucket).object(key).expiry((int) ttl.toSeconds()).build());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to presign object " + key, e);
    }
  }

  private MinioClient signer(String baseUrl) {
    MinioClient cached = signers.get(baseUrl);
    if (cached != null) {
      return cached;
    }
    MinioClient created = MinioClient.builder().endpoint(baseUrl).region(REGION)
        .credentials(accessKey, secretKey).build();
    if (signers.size() < MAX_CACHED_SIGNERS) {
      signers.put(baseUrl, created);
    }
    return created;
  }

  private String requestBaseUrl() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servlet) {
      HttpServletRequest request = servlet.getRequest();
      String host = request.getHeader("Host");
      if (host != null && HOST.matcher(host).matches()) {
        String proto =
            "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")) ? "https" : "http";
        return proto + "://" + host;
      }
    }
    return publicUrl;
  }

  private static String trimTrailingSlash(String url) {
    return url.replaceAll("/+$", "");
  }
}
