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
  const context = ImageManipulator.manipulate(asset.uri);
  if (Math.max(asset.width, asset.height) > MAX_EDGE) {
    context.resize(asset.width >= asset.height ? { width: MAX_EDGE } : { height: MAX_EDGE });
  }
  const rendered = await context.renderAsync();
  const result = await rendered.saveAsync({ compress: JPEG_QUALITY, format: SaveFormat.JPEG });
  return { uri: result.uri, name: "report.jpg", type: "image/jpeg" };
}
