// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.service;

import HaUI.ImplSolution.dxos.core.api.storage.FileStore;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// Stores uploaded disease photos; the image processor registered for the disease-report purpose
// decides what is accepted.
@Service
public class FileStorageService {

  private static final long MAX_BYTES = 10L * 1024 * 1024;

  private final FileStore fileStore;

  public FileStorageService(FileStore fileStore) {
    this.fileStore = fileStore;
  }

  // Returns the object key of the stored image.
  public String store(MultipartFile file, String uploadedBy) {
    return fileStore.store(DiseaseImageProcessor.PURPOSE, read(file), file.getOriginalFilename(),
        uploadedBy);
  }

  // The photo as it would be stored, for checks that must see the same pixels.
  public byte[] sanitize(MultipartFile file) {
    return fileStore.process(DiseaseImageProcessor.PURPOSE, read(file), file.getOriginalFilename())
        .data();
  }

  private static byte[] read(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
      throw new IllegalArgumentException("A JPEG/PNG image up to 10 MiB is required");
    }
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to read uploaded image", e);
    }
  }
}
