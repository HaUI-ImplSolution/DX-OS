// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import HaUI.ImplSolution.DX_Farm.core.storage.StorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {

  private final Map<String, byte[]> stored = new HashMap<>();
  private FileStorageService service;

  @BeforeEach
  void setUp() {
    StorageService storage = mock(StorageService.class);
    doAnswer(invocation -> stored.put(invocation.getArgument(0), invocation.getArgument(1)))
        .when(storage).putObject(anyString(), any(), anyString(), any());
    service = new FileStorageService(storage);
  }

  private static byte[] encode(int width, int height, String format) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }

  // Inserts an APP1 segment (EXIF with the given orientation, plus a marker string that stands
  // in for GPS data) right after the JPEG SOI marker.
  private static byte[] withExif(byte[] jpeg, int orientation, String secret) {
    byte[] tiff = {'M', 'M', 0, 0x2A, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0,
        (byte) orientation, 0, 0, 0, 0, 0, 0};
    byte[] marker = secret.getBytes(StandardCharsets.US_ASCII);
    int length = 2 + 6 + tiff.length + marker.length;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2);
    out.write(0xFF);
    out.write(0xE1);
    out.write(length >> 8);
    out.write(length & 0xFF);
    out.writeBytes(new byte[] {'E', 'x', 'i', 'f', 0, 0});
    out.writeBytes(tiff);
    out.writeBytes(marker);
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  private byte[] storedBytes(String key) {
    return stored.get(key);
  }

  private long storedFileCount() {
    return stored.size();
  }

  @Test
  void rejectsTextFileRenamedToJpg() throws IOException {
    var file = new MockMultipartFile("image", "leaf.jpg", "image/jpeg",
        "not an image".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> service.store(file, "user-1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(storedFileCount()).isZero();
  }

  @Test
  void rejectsCorruptJpegBody() throws IOException {
    byte[] corrupt = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03};
    var file = new MockMultipartFile("image", "leaf.jpg", "image/jpeg", corrupt);

    assertThatThrownBy(() -> service.store(file, "user-1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(storedFileCount()).isZero();
  }

  @Test
  void removesExifAndOtherEmbeddedMetadata() throws IOException {
    byte[] original = withExif(encode(40, 20, "jpg"), 1, "GPS-SECRET-10.77,106.69");
    assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("GPS-SECRET");
    var file = new MockMultipartFile("image", "leaf.jpg", "image/jpeg", original);

    byte[] stored = storedBytes(service.store(file, "user-1"));

    String content = new String(stored, StandardCharsets.ISO_8859_1);
    assertThat(content).doesNotContain("GPS-SECRET").doesNotContain("Exif");
    assertThat(ImageIO.read(new ByteArrayInputStream(stored)).getWidth()).isEqualTo(40);
  }

  @Test
  void bakesExifOrientationIntoPixels() throws IOException {
    byte[] original = withExif(encode(40, 20, "jpg"), 6, "x");
    var file = new MockMultipartFile("image", "leaf.jpg", "image/jpeg", original);

    BufferedImage stored =
        ImageIO.read(new ByteArrayInputStream(storedBytes(service.store(file, "user-1"))));

    assertThat(stored.getWidth()).isEqualTo(20);
    assertThat(stored.getHeight()).isEqualTo(40);
  }

  @Test
  void fileTypeComesFromContentNotFromClientMetadata() throws IOException {
    var file = new MockMultipartFile("image", "leaf.jpg", "image/jpeg", encode(30, 30, "png"));

    String filename = service.store(file, "user-1");

    assertThat(filename).endsWith(".png");
    assertThat(ImageIO.read(new ByteArrayInputStream(storedBytes(filename)))).isNotNull();
  }

  @Test
  void rejectsOtherImageFormats() throws IOException {
    var file = new MockMultipartFile("image", "leaf.gif", "image/gif", encode(30, 30, "gif"));

    assertThatThrownBy(() -> service.store(file, "user-1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(storedFileCount()).isZero();
  }
}
