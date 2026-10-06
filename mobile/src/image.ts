import { File } from "expo-file-system";
import { decode } from "jpeg-js";
import { ImageManipulator, SaveFormat } from "expo-image-manipulator";
import type { UploadFile } from "@/api/client";

const MAX_EDGE = 1280;
const JPEG_QUALITY = 0.7;

// Shrinks the longest edge to MAX_EDGE and re-encodes as JPEG so uploads stay well under the 10MB limit.
export async function prepareForUpload(asset: {
  uri: string;
  width: number;
  height: number;
}): Promise<UploadFile> {
  if (Math.min(asset.width, asset.height) < 160) {
    throw new Error("Ảnh quá nhỏ. Mỗi chiều phải từ 160px.");
  }
  const context = ImageManipulator.manipulate(asset.uri);
  if (Math.max(asset.width, asset.height) > MAX_EDGE) {
    context.resize(asset.width >= asset.height ? { width: MAX_EDGE } : { height: MAX_EDGE });
  }
  const rendered = await context.renderAsync();
  const result = await rendered.saveAsync({ compress: JPEG_QUALITY, format: SaveFormat.JPEG });
  const file = new File(result.uri);
  if (!file.size || file.size > 10 * 1024 * 1024) {
    throw new Error("Ảnh phải có dung lượng từ 1 byte đến 10 MiB.");
  }
  const thumbnail = ImageManipulator.manipulate(result.uri);
  thumbnail.resize({ width: 64 });
  const thumb = await (await thumbnail.renderAsync()).saveAsync({ format: SaveFormat.JPEG });
  const pixels = decode(await new File(thumb.uri).bytes(), { useTArray: true, formatAsRGBA: true });
  let sum = 0;
  for (let i = 0; i < pixels.data.length; i += 4) {
    sum += 0.299 * pixels.data[i] + 0.587 * pixels.data[i + 1] + 0.114 * pixels.data[i + 2];
  }
  const brightness = sum / (pixels.width * pixels.height);
  new File(thumb.uri).delete();
  if (brightness < 20 || brightness > 245) {
    throw new Error(brightness < 20 ? "Ảnh quá tối. Hãy chụp ở nơi đủ sáng." : "Ảnh quá sáng. Hãy tránh ánh sáng chiếu trực tiếp.");
  }
  return { uri: result.uri, name: "report.jpg", type: "image/jpeg" };
}
