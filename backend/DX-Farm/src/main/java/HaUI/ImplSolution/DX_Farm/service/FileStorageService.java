package HaUI.ImplSolution.DX_Farm.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

  private static final long MAX_IMAGE_PIXELS = 24_000_000L;
  private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "png", "gif", "bmp", "wbmp");

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
    String extension = validatedExtension(file);
    String filename = UUID.randomUUID() + "." + extension;
    Path target = uploadDir.resolve(filename);
    try {
      Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to store image file", e);
    }
    return filename;
  }

  private static String validatedExtension(MultipartFile file) {
    try (InputStream stream = file.getInputStream();
        ImageInputStream imageStream = ImageIO.createImageInputStream(stream)) {
      if (imageStream == null) {
        throw new IllegalArgumentException("Invalid image data");
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
      if (!readers.hasNext()) {
        throw new IllegalArgumentException("Invalid image data");
      }
      ImageReader reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase();
        if (!ALLOWED_FORMATS.contains(format)) {
          throw new IllegalArgumentException("Unsupported image format");
        }
        reader.setInput(imageStream, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
          throw new IllegalArgumentException("Image dimensions are not allowed");
        }
        BufferedImage decoded = reader.read(0);
        if (decoded == null) {
          throw new IllegalArgumentException("Invalid image data");
        }
        return "jpeg".equals(format) ? "jpg" : format;
      } finally {
        reader.dispose();
      }
    } catch (IOException exception) {
      throw new IllegalArgumentException("Invalid image data", exception);
    }
  }
}
