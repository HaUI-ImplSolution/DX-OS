// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useRef, useState } from "react";
import { queryAssistant, type AssistantQuery, type AssistantResult } from "./api";

export default function useAssistantQuery(scope: string) {
  const [result, setResult] = useState<AssistantResult | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const active = useRef<AbortController | null>(null);
  const timeout = useRef<ReturnType<typeof setTimeout>>();

  const cancel = useCallback(() => {
    active.current?.abort();
    active.current = null;
    clearTimeout(timeout.current);
    setBusy(false);
    setResult(null);
    setError("");
  }, []);

  useEffect(() => {
    cancel();
    return () => {
      active.current?.abort();
      active.current = null;
      clearTimeout(timeout.current);
    };
  }, [scope, cancel]);

  const run = useCallback(async (payload: AssistantQuery) => {
    active.current?.abort();
    clearTimeout(timeout.current);
    const controller = new AbortController();
    active.current = controller;
    setBusy(true);
    setResult(null);
    setError("");
    timeout.current = setTimeout(() => {
      if (active.current !== controller) return;
      active.current = null;
      controller.abort();
      setBusy(false);
      setError("Trợ lý đã hết thời gian chờ. Bạn có thể mở hồ sơ gốc hoặc thử lại sau.");
    }, 65000);
    try {
      const response = await queryAssistant(payload, controller.signal);
      if (active.current === controller) setResult(response);
    } catch (e) {
      if (active.current === controller && !controller.signal.aborted)
        setError(e instanceof Error ? e.message : "Không xử lý được yêu cầu.");
    } finally {
      if (active.current === controller) {
        active.current = null;
        clearTimeout(timeout.current);
        setBusy(false);
      }
    }
  }, []);

  return { result, error, busy, run, cancel };
}
