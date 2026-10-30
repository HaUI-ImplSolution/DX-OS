// SPDX-License-Identifier: Apache-2.0
import { File } from "expo-file-system";
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
  return { uri: result.uri, name: "report.jpg", type: "image/jpeg" };
}
