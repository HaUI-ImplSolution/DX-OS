package HaUI.ImplSolution.DX_Farm.core.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// Creates the private bucket on first start; the storage server may still be starting up.
@Component
public class StorageBucketInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(StorageBucketInitializer.class);
  private static final int ATTEMPTS = 10;
  private static final long RETRY_DELAY_MS = 3000;

  private final StorageService storageService;

  public StorageBucketInitializer(StorageService storageService) {
    this.storageService = storageService;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    Exception last = null;
    for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
      try {
        String bucket = storageService.bucket();
        if (!storageService.client()
            .bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
          storageService.client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
          log.info("Created storage bucket {}", bucket);
        }
        return;
      } catch (Exception e) {
        last = e;
        log.warn("Storage not ready (attempt {}/{}): {}", attempt, ATTEMPTS, e.getMessage());
        Thread.sleep(RETRY_DELAY_MS);
      }
    }
    throw new IllegalStateException("Object storage is unreachable", last);
  }
}
