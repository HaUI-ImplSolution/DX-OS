// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.service;

import HaUI.ImplSolution.DX_Farm.core.storage.StorageService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// Stores uploaded photos in object storage after decoding and re-encoding them, so the stored
// object is always a real JPEG/PNG whose type comes from its content and which carries no
// EXIF/GPS metadata.
@Service
public class FileStorageService {

  private static final long MAX_PIXELS = 40_000_000L;
  private static final float JPEG_QUALITY = 0.92f;

  private final StorageService storageService;

  public FileStorageService(StorageService storageService) {
    this.storageService = storageService;
  }

  // Returns the object key of the stored image.
  public String store(MultipartFile file, String uploadedBy) {
    byte[] sanitized = sanitize(file);
    ImageFormat format = detectFormat(sanitized);

    String key = UUID.randomUUID() + "." + format.extension;
    storageService.putObject(key, sanitized, format.contentType,
        Map.of("uploaded-by", uploadedBy, "source", "disease-report"));
    return key;
  }

  public byte[] sanitize(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
      throw new IllegalArgumentException("A JPEG/PNG image up to 10 MiB is required");
    }
    try {
      byte[] original = file.getBytes();
      ImageFormat format = detectFormat(original);
      BufferedImage decoded = decode(original);
      if (format == ImageFormat.JPEG) {
        decoded = applyOrientation(decoded, readJpegOrientation(original));
      }
      return encode(decoded, format);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to read uploaded image", e);
    }
  }

  private enum ImageFormat {
    JPEG("jpg", "jpeg", "image/jpeg"), PNG("png", "png", "image/png");

    private final String extension;
    private final String writerName;
    private final String contentType;

    ImageFormat(String extension, String writerName, String contentType) {
      this.extension = extension;
      this.writerName = writerName;
      this.contentType = contentType;
    }
  }

  private static ImageFormat detectFormat(byte[] data) {
    if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8
        && (data[2] & 0xFF) == 0xFF) {
      return ImageFormat.JPEG;
    }
    if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N'
        && data[3] == 'G' && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A
        && data[7] == 0x0A) {
      return ImageFormat.PNG;
    }
    throw new IllegalArgumentException("Only JPEG and PNG images are allowed");
  }

  private static BufferedImage decode(byte[] data) {
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new IllegalArgumentException("Uploaded file is not a valid image");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
          throw new IllegalArgumentException("Image resolution is too large");
        }
        return toStandardType(reader.read(0));
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof IllegalArgumentException illegalArgument) {
        throw illegalArgument;
      }
      throw new IllegalArgumentException("Uploaded file is not a valid image", e);
    }
  }

  // Normalizes any decoded raster to RGB/ARGB so later transforms and writers behave the same.
  private static BufferedImage toStandardType(BufferedImage source) {
    int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB
        : BufferedImage.TYPE_INT_RGB;
    if (source.getType() == type) {
      return source;
    }
    BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), type);
    Graphics2D graphics = converted.createGraphics();
    try {
      graphics.drawImage(source, 0, 0, null);
    } finally {
      graphics.dispose();
    }
    return converted;
  }

  private static byte[] encode(BufferedImage image, ImageFormat format) {
    BufferedImage toWrite = image;
    if (format == ImageFormat.JPEG && image.getColorModel().hasAlpha()) {
      toWrite = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = toWrite.createGraphics();
      try {
        graphics.drawImage(image, 0, 0, Color.WHITE, null);
      } finally {
        graphics.dispose();
      }
    }
    ImageWriter writer = ImageIO.getImageWritersByFormatName(format.writerName).next();
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      if (format == ImageFormat.JPEG) {
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
      }
      writer.setOutput(output);
      // Null metadata: nothing from the original file (EXIF, GPS, ICC, comments) is carried over.
      writer.write(null, new IIOImage(toWrite, null, null), param);
      output.flush();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to encode image", e);
    } finally {
      writer.dispose();
    }
  }

  // EXIF orientation is baked into the pixels because the tag itself is dropped on re-encode.
  private static BufferedImage applyOrientation(BufferedImage image,
      int orientation) {int w=image.getWidth();int h=image.getHeight();AffineTransform transform;boolean swap=false;switch(orientation){case 2->transform=new AffineTransform(-1,0,0,1,w,0);case 3->transform=new AffineTransform(-1,0,0,-1,w,h);case 4->transform=new AffineTransform(1,0,0,-1,0,h);case 5->{transform=new AffineTransform(0,1,1,0,0,0);swap=true;}case 6->{transform=new AffineTransform(0,1,-1,0,h,0);swap=true;}case 7->{transform=new AffineTransform(0,-1,-1,0,h,w);swap=true;}case 8->{transform=new AffineTransform(0,-1,1,0,0,w);swap=true;}default->{return image;}}BufferedImage rotated=new BufferedImage(swap?h:w,swap?w:h,image.getType());Graphics2D graphics=rotated.createGraphics();try{graphics.drawImage(image,transform,null);}finally{graphics.dispose();}return rotated;}

  // Returns the EXIF orientation (1-8) of a JPEG, or 1 when absent or unreadable.
  static int readJpegOrientation(byte[] data) {
    int pos = 2;
    while (pos + 4 <= data.length) {
      if ((data[pos] & 0xFF) != 0xFF) {
        return 1;
      }
      int marker = data[pos + 1] & 0xFF;
      if (marker == 0xDA || marker == 0xD9) {
        return 1;
      }
      int length = u16(data, pos + 2, false);
      if (marker == 0xE1 && length >= 14 && pos + 2 + length <= data.length && data[pos + 4] == 'E'
          && data[pos + 5] == 'x' && data[pos + 6] == 'i' && data[pos + 7] == 'f'
          && data[pos + 8] == 0 && data[pos + 9] == 0) {
        return tiffOrientation(data, pos + 10, pos + 2 + length);
      }
      pos += 2 + length;
    }
    return 1;
  }

  private static int tiffOrientation(byte[] data, int tiff, int end) {
    if (tiff + 8 > end) {
      return 1;
    }
    boolean little;
    if (data[tiff] == 'I' && data[tiff + 1] == 'I') {
      little = true;
    } else if (data[tiff] == 'M' && data[tiff + 1] == 'M') {
      little = false;
    } else {
      return 1;
    }
    long ifd = u32(data, tiff + 4, little);
    long entries = tiff + ifd;
    if (entries < 0 || entries + 2 > end) {
      return 1;
    }
    int count = u16(data, (int) entries, little);
    for (int i = 0; i < count; i++) {
      int entry = (int) entries + 2 + i * 12;
      if (entry + 12 > end) {
        return 1;
      }
      if (u16(data, entry, little) == 0x0112) {
        int value = u16(data, entry + 8, little);
        return value >= 1 && value <= 8 ? value : 1;
      }
    }
    return 1;
  }

  private static int u16(byte[] data, int offset, boolean little) {
    int a = data[offset] & 0xFF;
    int b = data[offset + 1] & 0xFF;
    return little ? (b << 8) | a : (a << 8) | b;
  }

  private static long u32(byte[] data, int offset, boolean little) {
    long low = u16(data, little ? offset : offset + 2, little);
    long high = u16(data, little ? offset + 2 : offset, little);
    return (high << 16) | low;
  }
}
