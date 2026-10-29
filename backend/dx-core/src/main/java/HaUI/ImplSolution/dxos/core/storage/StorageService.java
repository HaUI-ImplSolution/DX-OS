// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.storage;

import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import jakarta.annotation.PreDestroy;
import java.net.URI;
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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

// Object storage on an S3-compatible server. Objects are private; clients and other services read
// them through time-limited presigned URLs.
@Service
public class StorageService implements ObjectStorage {

  // Fixed so signing a URL is pure computation and never asks the server for the bucket region.
  private static final Region REGION = Region.US_EAST_1;
  private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]+(:\\d{1,5})?");
  private static final int MAX_CACHED_SIGNERS = 32;
  private static final S3Configuration PATH_STYLE =
      S3Configuration.builder().pathStyleAccessEnabled(true).build();

  private final S3Client client;
  private final String bucket;
  private final String endpoint;
  private final StaticCredentialsProvider credentials;
  private final String publicUrl;
  private final Duration presignTtl;
  private final Map<String, S3Presigner> signers = new ConcurrentHashMap<>();

  public StorageService(@Value("${storage.endpoint}") String endpoint,
      @Value("${storage.access-key}") String accessKey,
      @Value("${storage.secret-key}") String secretKey, @Value("${storage.bucket}") String bucket,
      @Value("${storage.public-url}") String publicUrl,
      @Value("${storage.presign-ttl}") Duration presignTtl) {
    this.endpoint = trimTrailingSlash(endpoint);
    this.credentials =
        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    this.bucket = bucket;
    this.publicUrl = trimTrailingSlash(publicUrl);
    this.presignTtl = presignTtl;
    this.client = S3Client.builder().endpointOverride(URI.create(this.endpoint)).region(REGION)
        .credentialsProvider(credentials).serviceConfiguration(PATH_STYLE)
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
  }

  public String bucket() {
    return bucket;
  }

  // Creates the bucket when it is missing; true when it was created. Fails while the server is
  // unreachable.
  public boolean ensureBucket() {
    if (bucketExists()) {
      return false;
    }
    client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    return true;
  }

  @Override
  public void putObject(String key, byte[] data, String contentType, Map<String, String> metadata) {
    try {
      client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
          .metadata(metadata).build(), RequestBody.fromBytes(data));
    } catch (RuntimeException e) {
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
      return bucketExists();
    } catch (RuntimeException e) {
      return false;
    }
  }

  @PreDestroy
  void close() {
    client.close();
    signers.values().forEach(S3Presigner::close);
  }

  private boolean bucketExists() {
    try {
      client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
      return true;
    } catch (NoSuchBucketException e) {
      return false;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return false;
      }
      throw e;
    }
  }

  private String presign(String baseUrl, String key, Duration ttl) {
    try {
      GetObjectPresignRequest request = GetObjectPresignRequest.builder().signatureDuration(ttl)
          .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build()).build();
      return signer(baseUrl).presignGetObject(request).url().toString();
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to presign object " + key, e);
    }
  }

  private S3Presigner signer(String baseUrl) {
    S3Presigner cached = signers.get(baseUrl);
    if (cached != null) {
      return cached;
    }
    S3Presigner created = S3Presigner.builder().endpointOverride(URI.create(baseUrl)).region(REGION)
        .credentialsProvider(credentials).serviceConfiguration(PATH_STYLE).build();
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
        // With server.forward-headers-strategy=framework the filter consumes X-Forwarded-Proto
        // and exposes it as the request scheme; without it the raw header is still present.
        String forwarded = request.getHeader("X-Forwarded-Proto");
        String scheme = forwarded != null ? forwarded : request.getScheme();
        String proto = "https".equalsIgnoreCase(scheme) ? "https" : "http";
        return proto + "://" + host;
      }
    }
    return publicUrl;
  }

  private static String trimTrailingSlash(String url) {
    return url.replaceAll("/+$", "");
  }
}
