// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.files;

import HaUI.ImplSolution.dxos.core.api.storage.FileProcessor;
import HaUI.ImplSolution.dxos.core.api.storage.ProcessedFile;
import java.net.URLConnection;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

// Files attached to records are stored as they were sent; only the name is checked.
@Component
public class AttachmentProcessor implements FileProcessor {

  public static final String PURPOSE = "attachment";

  private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{1,8}");

  @Override
  public String purpose() {
    return PURPOSE;
  }

  @Override
  public ProcessedFile process(byte[] original, String filename) {
    if (original == null || original.length == 0) {
      throw new IllegalArgumentException("The file is empty");
    }
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("The file has no name");
    }
    int dot = filename.lastIndexOf('.');
    String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    String contentType = URLConnection.guessContentTypeFromName(filename);
    return new ProcessedFile(original,
        contentType == null ? "application/octet-stream" : contentType,
        EXTENSION.matcher(extension).matches() ? extension : "bin");
  }
}
