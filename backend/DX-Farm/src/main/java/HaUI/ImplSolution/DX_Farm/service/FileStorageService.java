package HaUI.ImplSolution.DX_Farm.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

  private static final Map<String, String> ALLOWED_IMAGE_TYPES =
      Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png");

  private final Path uploadDir;

  public FileStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir) {
    this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.uploadDir);
    } catch (IOException e) {
      throw new IllegalStateException("Could not create upload directory: " + this.uploadDir, e);
    }
  }

  public String store(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Image file is required");
    }
    String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
    String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    String expectedContentType = ALLOWED_IMAGE_TYPES.get(normalizedExtension);
    String contentType = file.getContentType();
    if (expectedContentType == null || !expectedContentType.equalsIgnoreCase(contentType)) {
      throw new IllegalArgumentException("Only matching JPEG or PNG uploads are allowed");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read image file", e);
    }
    validateImageBytes(bytes, normalizedExtension);

    String filename = UUID.randomUUID() + "." + normalizedExtension;
    Path target = uploadDir.resolve(filename);
    try {
      Files.write(target, bytes);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to store image file", e);
    }
    return filename;
  }

  private static void validateImageBytes(byte[] bytes, String extension) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
        throw new IllegalArgumentException("Invalid image data");
      }
      boolean pngSignature = bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
          && bytes[2] == 0x4e && bytes[3] == 0x47;
      boolean jpegSignature = bytes.length >= 3 && bytes[0] == (byte) 0xff
          && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
      if (("png".equals(extension) && !pngSignature)
          || (("jpg".equals(extension) || "jpeg".equals(extension)) && !jpegSignature)) {
        throw new IllegalArgumentException("Image content does not match its file extension");
      }
    } catch (IOException exception) {
      throw new IllegalArgumentException("Invalid image data", exception);
    }
  }
}
