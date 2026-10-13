// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.storage.FileProcessor;
import HaUI.ImplSolution.dxos.core.api.storage.ProcessedFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Component;

// Accepts JPEG, PNG and PDF files up to 5 MiB. The type comes from the content, not from the
// file name.
@Component
public class TicketAttachmentProcessor implements FileProcessor {

  public static final String PURPOSE = "ticket-attachment";
  public static final long MAX_BYTES = 5L * 1024 * 1024;

  @Override
  public String purpose() {
    return PURPOSE;
  }

  @Override
  public ProcessedFile process(byte[] original, String filename) {
    if (original == null || original.length == 0) {
      throw new IllegalArgumentException("The attached file is empty");
    }
    if (original.length > MAX_BYTES) {
      throw new IllegalArgumentException("An attachment may be at most 5 MiB");
    }
    if (startsWith(original, 0xFF, 0xD8, 0xFF)) {
      return new ProcessedFile(original, "image/jpeg", "jpg");
    }
    if (startsWith(original, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return new ProcessedFile(original, "image/png", "png");
    }
    byte[] pdf = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    if (original.length >= pdf.length && Arrays.equals(Arrays.copyOf(original, pdf.length), pdf)) {
      return new ProcessedFile(original, "application/pdf", "pdf");
    }
    throw new IllegalArgumentException("Only JPEG, PNG and PDF files can be attached");
  }

  // Content type of a stored object, from the extension the processor gave its key.
  public static String contentTypeOfKey(String objectKey) {
    if (objectKey.endsWith(".png")) {
      return "image/png";
    }
    if (objectKey.endsWith(".pdf")) {
      return "application/pdf";
    }
    return "image/jpeg";
  }

  private static boolean startsWith(byte[] data, int... prefix) {
    if (data.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if ((data[i] & 0xFF) != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
