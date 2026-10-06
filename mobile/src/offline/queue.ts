import AsyncStorage from "@react-native-async-storage/async-storage";
import { Directory, File, Paths } from "expo-file-system";

const STORAGE_KEY = "dxfarm.uploadQueue";

export type QueueStatus = "pending" | "sending" | "failed";

export interface QueueItem {
  // Doubles as the Idempotency-Key of the upload.
  id: string;
  username: string;
  cropSeasonId: number;
  retakeOf?: number;
  parcelCode: string | null;
  // Photo copied into the app's document directory.
  uri: string;
  createdAt: string;
  attempts: number;
  status: QueueStatus;
  lastError: string | null;
  // Epoch milliseconds before which the item is not retried.
  nextAttemptAt: number;
}

export async function loadQueue(): Promise<QueueItem[]> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as QueueItem[]) : [];
  } catch {
    return [];
  }
}

export const saveQueue = (items: QueueItem[]) =>
  AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(items));

// Copies the photo out of the cache directory so it survives until it is uploaded.
export function storeQueuedPhoto(id: string, sourceUri: string): string {
  const directory = new Directory(Paths.document, "upload-queue");
  directory.create({ intermediates: true, idempotent: true });
  const destination = new File(directory, `${id}.jpg`);
  new File(sourceUri).copy(destination);
  return destination.uri;
}

export function deleteQueuedPhoto(uri: string) {
  try {
    const file = new File(uri);
    if (file.exists) file.delete();
  } catch {
    // The file is already gone.
  }
}
