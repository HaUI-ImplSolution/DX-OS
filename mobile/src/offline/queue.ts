import AsyncStorage from "@react-native-async-storage/async-storage";
import { Directory, File, Paths } from "expo-file-system";

const STORAGE_KEY = "farm.uploadQueue";

export type QueueStatus = "pending" | "sending" | "failed";

// A queued photo report, a new parcel or a new crop season.
export type QueueKind = "report" | "parcel" | "season";

export interface QueueItem {
  // Doubles as the Idempotency-Key of the request.
  id: string;
  username: string;
  // Absent on legacy items, which are always reports.
  kind?: QueueKind;
  // Reports only.
  cropSeasonId: number | null;
  retakeOf?: number;
  // Reports: where the photo was taken. Seasons: the parcel the season is for.
  parcelCode: string | null;
  // Reports only: photo copied into the app's document directory.
  uri: string | null;
  // Parcels and seasons: the request body as JSON.
  payload?: string;
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

export const kindOf = (item: QueueItem): QueueKind => item.kind ?? "report";

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
