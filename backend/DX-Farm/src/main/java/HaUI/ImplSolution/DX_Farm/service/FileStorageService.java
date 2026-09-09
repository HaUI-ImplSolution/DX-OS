package HaUI.ImplSolution.DX_Farm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

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
    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
      throw new IllegalArgumentException("Only image uploads are allowed");
    }
    String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
    String filename = UUID.randomUUID() + (extension != null ? "." + extension : "");
    Path target = uploadDir.resolve(filename);
    try {
      Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to store image file", e);
    }
    return filename;
  }
}
