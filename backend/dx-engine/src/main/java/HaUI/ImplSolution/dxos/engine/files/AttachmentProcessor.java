// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.files;

import HaUI.ImplSolution.dxos.core.api.storage.FileProcessor;
import HaUI.ImplSolution.dxos.core.api.storage.ProcessedFile;
import java.net.URLConnection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Files attached to records are stored as they were sent; the name and the type are checked.
// An empty list of allowed extensions accepts every type.
@Component
public class AttachmentProcessor implements FileProcessor {

  public static final String PURPOSE = "attachment";

  private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{1,8}");

  private final Set<String> allowed;

  public AttachmentProcessor(
      @Value(
              "${engine.files.allowed-extensions:pdf,png,jpg,jpeg,gif,webp,"
                  + "txt,csv,md,doc,docx,xls,xlsx,ppt,pptx,odt,ods,odp,zip}")
          List<String> allowed) {
    this.allowed =
        allowed.stream()
            .map(item -> item.strip().toLowerCase(Locale.ROOT))
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toSet());
  }

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
    if (!allowed.isEmpty() && !allowed.contains(extension)) {
      throw new IllegalArgumentException("Files of this type are not accepted");
    }
    String contentType = URLConnection.guessContentTypeFromName(filename);
    return new ProcessedFile(
        original,
        contentType == null ? "application/octet-stream" : contentType,
        EXTENSION.matcher(extension).matches() ? extension : "bin");
  }
}
