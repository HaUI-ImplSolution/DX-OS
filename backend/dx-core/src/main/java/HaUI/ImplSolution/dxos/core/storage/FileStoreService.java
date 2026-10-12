// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.storage;

import HaUI.ImplSolution.dxos.core.api.storage.FileProcessor;
import HaUI.ImplSolution.dxos.core.api.storage.FileStore;
import HaUI.ImplSolution.dxos.core.api.storage.ObjectStorage;
import HaUI.ImplSolution.dxos.core.api.storage.ProcessedFile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

// Every stored upload went through the processor of its purpose; there is no way to store a file
// without one.
@Service
public class FileStoreService implements FileStore {

  private final ObjectStorage objectStorage;
  private final Map<String, FileProcessor> processors = new HashMap<>();

  public FileStoreService(ObjectStorage objectStorage, ObjectProvider<FileProcessor> providers) {
    this.objectStorage = objectStorage;
    providers.orderedStream().forEach(processor -> processors.put(processor.purpose(), processor));
  }

  @Override
  public ProcessedFile process(String purpose, byte[] original, String filename) {
    FileProcessor processor = processors.get(purpose);
    if (processor == null) {
      throw new IllegalArgumentException("No file processor registered for: " + purpose);
    }
    return processor.process(original, filename);
  }

  @Override
  public String store(String purpose, byte[] original, String filename, String uploadedBy) {
    ProcessedFile processed = process(purpose, original, filename);
    String key = UUID.randomUUID() + "." + processed.extension();
    objectStorage.putObject(key, processed.data(), processed.contentType(),
        Map.of("uploaded-by", uploadedBy, "source", purpose));
    return key;
  }
}
