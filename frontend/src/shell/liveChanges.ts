// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
// Tells the screens that a record changed somewhere, so they read their data again. The backend keeps
// one event stream open per browser tab and sends a bare "changed" notice with no record content; the
// stream is read with fetch because the token goes in a header, which EventSource cannot set.
import { API_V1 } from "./api";
import { getToken } from "./auth";

const RETRY_MS = 3000;
const RETRY_MAX_MS = 30000;
// Notices that arrive within this time reach the screens as one.
const COALESCE_MS = 1000;

const listeners = new Set<() => void>();
let stop: AbortController | null = null;
let timer: ReturnType<typeof setTimeout> | null = null;

function notify() {
  if (timer) return;
  timer = setTimeout(() => {
    timer = null;
    listeners.forEach((listener) => listener());
  }, COALESCE_MS);
}

function sleep(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve) => {
    const id = setTimeout(resolve, ms);
    signal.addEventListener("abort", () => {
      clearTimeout(id);
      resolve();
    });
  });
}

async function read(body: ReadableStream<Uint8Array>) {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) return;
    buffer += decoder.decode(value, { stream: true });
    let end = buffer.indexOf("\n\n");
    while (end >= 0) {
      const frame = buffer.slice(0, end);
      buffer = buffer.slice(end + 2);
      // The space after the colon is optional in an event stream.
      if (frame.split("\n").some((line) => /^event: ?changed\r?$/.test(line))) notify();
      end = buffer.indexOf("\n\n");
    }
  }
}

// Keeps a stream open until aborted; the server ends it now and then and the next one carries a fresh token.
async function run(signal: AbortSignal) {
  let delay = RETRY_MS;
  while (!signal.aborted) {
    try {
      const token = await getToken();
      if (token) {
        const response = await fetch(`${API_V1}/inbox/stream`, {
          headers: { Authorization: `Bearer ${token}`, Accept: "text/event-stream" },
          signal,
        });
        if (response.ok && response.body) {
          delay = RETRY_MS;
          await read(response.body);
        }
      }
    } catch {
      // A dropped connection is retried below.
    }
    await sleep(delay, signal);
    delay = Math.min(delay * 2, RETRY_MAX_MS);
  }
}

// Calls the listener, at most about once a second, whenever a record changes. The stream is open while
// at least one listener exists. Returns the function that removes the listener.
export function onLiveChange(listener: () => void): () => void {
  listeners.add(listener);
  if (listeners.size === 1) {
    stop = new AbortController();
    void run(stop.signal);
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) {
      stop?.abort();
      stop = null;
    }
  };
}
