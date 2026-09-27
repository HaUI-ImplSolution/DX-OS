import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { AppState } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import { ApiError } from "@/api/client";
import { createCropSeason, createDiseaseReport, createParcel } from "@/api/endpoints";
import { useAuth } from "@/auth/AuthProvider";
import {
  deleteQueuedPhoto,
  kindOf,
  loadQueue,
  saveQueue,
  storeQueuedPhoto,
  type QueueItem,
  type QueueKind,
} from "./queue";

const MAX_ATTEMPTS = 8;
const BASE_DELAY_MS = 5_000;
const MAX_DELAY_MS = 300_000;

type EnqueueInput =
  | { id: string; kind?: "report"; cropSeasonId: number; parcelCode: string | null; uri: string }
  | { id: string; kind: "parcel" | "season"; parcelCode: string | null; payload: string };

interface QueueContextValue {
  // Items of the signed-in user.
  items: QueueItem[];
  // Increments each time a queued report is uploaded.
  sentCount: number;
  enqueue: (input: EnqueueInput) => Promise<void>;
  remove: (id: string) => void;
}

const QueueContext = createContext<QueueContextValue | null>(null);

const backoff = (attempts: number) => Math.min(BASE_DELAY_MS * 2 ** (attempts - 1), MAX_DELAY_MS);

// Connectivity, server and rate-limit errors are worth retrying; other 4xx will not succeed.
const isRetryable = (error: unknown) =>
  error instanceof ApiError &&
  (error.status === 0 || error.status >= 500 || [408, 409, 429].includes(error.status));

// Sends one queued item; its id is the Idempotency-Key, so a repeat can never create a second one.
async function send(item: QueueItem) {
  const kind: QueueKind = kindOf(item);
  if (kind === "parcel") {
    await createParcel(JSON.parse(item.payload ?? "{}"), item.id);
  } else if (kind === "season") {
    await createCropSeason(item.parcelCode ?? "", JSON.parse(item.payload ?? "{}"), item.id);
  } else {
    await createDiseaseReport(
      item.cropSeasonId ?? 0,
      { uri: item.uri ?? "", name: "report.jpg", type: "image/jpeg" },
      undefined,
      item.id,
    );
  }
}

export function QueueProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const username = user?.username ?? null;
  const [all, setAll] = useState<QueueItem[]>([]);
  const [sentCount, setSentCount] = useState(0);
  const itemsRef = useRef<QueueItem[]>([]);
  const [ready, setReady] = useState(false);
  const loaded = useRef(false);
  const flushing = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const commit = useCallback((next: QueueItem[]) => {
    itemsRef.current = next;
    setAll(next);
    void saveQueue(next);
  }, []);

  const patch = useCallback(
    (id: string, changes: Partial<QueueItem>) =>
      commit(itemsRef.current.map((item) => (item.id === id ? { ...item, ...changes } : item))),
    [commit],
  );

  const flush = useCallback(
    async (immediate: boolean) => {
      if (flushing.current || !username || !loaded.current) return;
      flushing.current = true;
      if (timer.current) clearTimeout(timer.current);
      try {
        if (immediate) {
          commit(
            itemsRef.current.map((item) =>
              item.username === username && item.status === "pending"
                ? { ...item, nextAttemptAt: 0 }
                : item,
            ),
          );
        }
        for (;;) {
          const next = itemsRef.current.find(
            (item) =>
              item.username === username &&
              item.status === "pending" &&
              item.nextAttemptAt <= Date.now(),
          );
          if (!next) break;
          patch(next.id, { status: "sending" });
          try {
            await send(next);
            if (next.uri) deleteQueuedPhoto(next.uri);
            commit(itemsRef.current.filter((item) => item.id !== next.id));
            setSentCount((count) => count + 1);
          } catch (error) {
            if (error instanceof ApiError && error.status === 401) {
              // Signed out: keep the item and resume after the next sign-in.
              patch(next.id, { status: "pending" });
              break;
            }
            const attempts = next.attempts + 1;
            const message = error instanceof Error ? error.message : "Không gửi được.";
            if (isRetryable(error) && attempts < MAX_ATTEMPTS) {
              patch(next.id, {
                status: "pending",
                attempts,
                lastError: message,
                nextAttemptAt: Date.now() + backoff(attempts),
              });
              break;
            }
            patch(next.id, { status: "failed", attempts, lastError: message });
          }
        }
      } finally {
        flushing.current = false;
        const waiting = itemsRef.current
          .filter((item) => item.username === username && item.status === "pending")
          .map((item) => item.nextAttemptAt);
        if (waiting.length > 0) {
          const delay = Math.max(Math.min(...waiting) - Date.now(), 1_000);
          timer.current = setTimeout(() => void flush(false), delay);
        }
      }
    },
    [username, commit, patch],
  );

  useEffect(() => {
    let cancelled = false;
    void loadQueue().then((stored) => {
      if (cancelled) return;
      // An item still marked "sending" was interrupted by the app closing.
      commit(stored.map((item) => (item.status === "sending" ? { ...item, status: "pending" } : item)));
      loaded.current = true;
      setReady(true);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!username || !ready) return;
    void flush(true);
    const unsubscribeNet = NetInfo.addEventListener((state) => {
      if (state.isConnected && state.isInternetReachable !== false) void flush(true);
    });
    const appState = AppState.addEventListener("change", (nextState) => {
      if (nextState === "active") void flush(true);
    });
    return () => {
      unsubscribeNet();
      appState.remove();
      if (timer.current) clearTimeout(timer.current);
    };
  }, [username, ready, flush]);

  const enqueue = useCallback(
    async (input: EnqueueInput) => {
      if (!username) return;
      const base =
        "payload" in input
          ? {
              kind: input.kind,
              cropSeasonId: null,
              parcelCode: input.parcelCode,
              uri: null,
              payload: input.payload,
            }
          : {
              kind: "report" as const,
              cropSeasonId: input.cropSeasonId,
              parcelCode: input.parcelCode,
              uri: storeQueuedPhoto(input.id, input.uri),
            };
      const item: QueueItem = {
        id: input.id,
        username,
        ...base,
        createdAt: new Date().toISOString(),
        attempts: 1,
        status: "pending",
        lastError: null,
        nextAttemptAt: Date.now() + backoff(1),
      };
      commit([...itemsRef.current.filter((existing) => existing.id !== item.id), item]);
      void flush(false);
    },
    [username, commit, flush],
  );

  const remove = useCallback(
    (id: string) => {
      const item = itemsRef.current.find((existing) => existing.id === id);
      if (!item || item.status === "sending") return;
      if (item.uri) deleteQueuedPhoto(item.uri);
      commit(itemsRef.current.filter((existing) => existing.id !== id));
    },
    [commit],
  );

  const items = useMemo(() => all.filter((item) => item.username === username), [all, username]);
  const value = useMemo(
    () => ({ items, sentCount, enqueue, remove }),
    [items, sentCount, enqueue, remove],
  );
  return <QueueContext.Provider value={value}>{children}</QueueContext.Provider>;
}

export function useQueue(): QueueContextValue {
  const context = useContext(QueueContext);
  if (!context) throw new Error("useQueue phải nằm trong QueueProvider");
  return context;
}
