// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { getToken } = vi.hoisted(() => ({ getToken: vi.fn() }));
vi.mock("./auth", () => ({ getToken }));
vi.mock("./api", () => ({ API_V1: "http://gateway/api/v1" }));

import { onLiveChange } from "./liveChanges";

// A stream the test writes to; the notice is sent the way the server frames it.
function stream() {
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  const body = new ReadableStream<Uint8Array>({ start: (c) => (controller = c) });
  const encoder = new TextEncoder();
  return { body, send: (text: string) => controller.enqueue(encoder.encode(text)), end: () => controller.close() };
}

const fetchMock = vi.fn();

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal("fetch", fetchMock);
  getToken.mockReset();
  fetchMock.mockReset();
  getToken.mockResolvedValue("token-1");
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("the live change stream", () => {
  it("opens the stream with the token and tells the listener about a change", async () => {
    const live = stream();
    fetchMock.mockResolvedValue({ ok: true, body: live.body });
    const listener = vi.fn();
    const off = onLiveChange(listener);
    await vi.advanceTimersByTimeAsync(0);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://gateway/api/v1/inbox/stream");
    expect(init.headers).toEqual({ Authorization: "Bearer token-1", Accept: "text/event-stream" });

    live.send("event: changed\n\n");
    await vi.advanceTimersByTimeAsync(0);
    expect(listener).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1000);
    expect(listener).toHaveBeenCalledTimes(1);
    off();
  });

  it("joins notices that arrive within a second into one", async () => {
    const live = stream();
    fetchMock.mockResolvedValue({ ok: true, body: live.body });
    const listener = vi.fn();
    const off = onLiveChange(listener);
    await vi.advanceTimersByTimeAsync(0);

    live.send("event: changed\n\nevent: changed\n\n");
    await vi.advanceTimersByTimeAsync(300);
    live.send("event:changed\r\n\n");
    await vi.advanceTimersByTimeAsync(1000);
    expect(listener).toHaveBeenCalledTimes(1);
    off();
  });

  it("reads a notice that arrives in pieces and ignores other events", async () => {
    const live = stream();
    fetchMock.mockResolvedValue({ ok: true, body: live.body });
    const listener = vi.fn();
    const off = onLiveChange(listener);
    await vi.advanceTimersByTimeAsync(0);

    live.send("event: ping\n\n: comment\n\nevent: chan");
    await vi.advanceTimersByTimeAsync(2000);
    expect(listener).not.toHaveBeenCalled();
    live.send("ged\n\n");
    await vi.advanceTimersByTimeAsync(1000);
    expect(listener).toHaveBeenCalledTimes(1);
    off();
  });

  it("serves every listener with one stream and closes it with the last", async () => {
    const live = stream();
    fetchMock.mockResolvedValue({ ok: true, body: live.body });
    const first = vi.fn();
    const second = vi.fn();
    const offFirst = onLiveChange(first);
    const offSecond = onLiveChange(second);
    await vi.advanceTimersByTimeAsync(0);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    live.send("event: changed\n\n");
    await vi.advanceTimersByTimeAsync(1000);
    expect(first).toHaveBeenCalledTimes(1);
    expect(second).toHaveBeenCalledTimes(1);

    const signal = fetchMock.mock.calls[0][1].signal as AbortSignal;
    offFirst();
    expect(signal.aborted).toBe(false);
    offSecond();
    expect(signal.aborted).toBe(true);
  });

  it("opens a fresh stream after the server ends one", async () => {
    const first = stream();
    const second = stream();
    fetchMock.mockResolvedValueOnce({ ok: true, body: first.body }).mockResolvedValueOnce({ ok: true, body: second.body });
    const off = onLiveChange(vi.fn());
    await vi.advanceTimersByTimeAsync(0);
    first.end();
    await vi.advanceTimersByTimeAsync(3000);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    off();
  });

  it("waits for a token before it opens a stream", async () => {
    getToken.mockResolvedValue(null);
    const off = onLiveChange(vi.fn());
    await vi.advanceTimersByTimeAsync(0);
    expect(fetchMock).not.toHaveBeenCalled();

    getToken.mockResolvedValue("token-2");
    fetchMock.mockResolvedValue({ ok: true, body: stream().body });
    await vi.advanceTimersByTimeAsync(3000);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    off();
  });

  it("retries a refused or dropped connection with a growing wait", async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, body: null }).mockRejectedValueOnce(new TypeError("offline"));
    fetchMock.mockResolvedValue({ ok: true, body: stream().body });
    const off = onLiveChange(vi.fn());
    await vi.advanceTimersByTimeAsync(0);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(2999);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(5999);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    off();
  });
});
