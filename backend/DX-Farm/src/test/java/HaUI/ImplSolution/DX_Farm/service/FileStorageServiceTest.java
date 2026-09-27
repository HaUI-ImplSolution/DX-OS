// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {

  @TempDir
  Path uploadDir;

  @Test
  void storesValidPng() throws Exception {
    byte[] image = pngImage();
    var file = new MockMultipartFile("image", "leaf.png", "image/png", image);

    String storedName = new FileStorageService(uploadDir.toString()).store(file);

    assertThat(storedName).endsWith(".png");
    assertThat(Files.readAllBytes(uploadDir.resolve(storedName))).isEqualTo(image);
  }

  @Test
  void rejectsFakeImageWithoutLeavingFile() {
    var file = new MockMultipartFile("image", "leaf.png", "image/png", "not-an-image".getBytes());

    assertThatThrownBy(() -> new FileStorageService(uploadDir.toString()).store(file))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid image data");
    assertThat(uploadDir).isEmptyDirectory();
  }

  @Test
  void determinesFormatFromContentInsteadOfClaimedMimeOrExtension() throws Exception {
    var file = new MockMultipartFile("image", "leaf.jpg", "image/png", pngImage());

    String storedName = new FileStorageService(uploadDir.toString()).store(file);

    assertThat(storedName).endsWith(".png");
    assertThat(Files.readAllBytes(uploadDir.resolve(storedName))).isEqualTo(pngImage());
  }

  private static byte[] pngImage() throws Exception {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
    return output.toByteArray();
  }
}
