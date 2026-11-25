// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttachmentProcessorTest {

  private static final byte[] RAR4 = {'R', 'a', 'r', '!', 0x1A, 0x07, 0x00, 1, 2, 3};
  private static final byte[] RAR5 = {'R', 'a', 'r', '!', 0x1A, 0x07, 0x01, 0x00, 1, 2};

  private final AttachmentProcessor processor = new AttachmentProcessor(List.of("zip", "pdf"));

  @Test
  void aRarArchiveIsRefusedWhateverItsNameSays() {
    assertThrows(IllegalArgumentException.class, () -> processor.process(RAR4, "evidence.zip"));
    assertThrows(IllegalArgumentException.class, () -> processor.process(RAR5, "evidence.pdf"));
  }

  @Test
  void aRarArchiveIsRefusedWhenEveryExtensionIsAccepted() {
    AttachmentProcessor open = new AttachmentProcessor(List.of());
    assertThrows(IllegalArgumentException.class, () -> open.process(RAR4, "evidence.rar"));
  }

  @Test
  void aZipFileIsAccepted() {
    byte[] zip = "PK\u0003\u0004zip".getBytes(StandardCharsets.ISO_8859_1);
    assertEquals("zip", processor.process(zip, "evidence.zip").extension());
  }
}
