// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useRef, useState } from "react";

export interface AsyncState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
  refreshing: boolean;
  reload: () => void;
  refresh: () => void;
}

const errorMessage = (error: unknown) =>
  error instanceof Error && error.message ? error.message : "Có lỗi xảy ra. Thử lại sau.";

// Runs `load` on mount and whenever `deps` change; a stale response never overwrites a newer one.
// `reload` and `refresh` keep the same identity until `deps` change.
export function useAsync<T>(load: () => Promise<T>, deps: readonly unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const latest = useRef(0);

  const run = useCallback(load, deps);

  const execute = useCallback(
    async (mode: "reload" | "refresh") => {
      const id = ++latest.current;
      if (mode === "reload") {
        setLoading(true);
        setError(null);
      } else {
        setRefreshing(true);
      }
      try {
        const result = await run();
        if (id !== latest.current) return;
        setData(result);
        setError(null);
      } catch (e) {
        if (id === latest.current) setError(errorMessage(e));
      } finally {
        if (id === latest.current) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    },
    [run],
  );

  useEffect(() => {
    void execute("reload");
  }, [execute]);

  const reload = useCallback(() => void execute("reload"), [execute]);
  const refresh = useCallback(() => void execute("refresh"), [execute]);

  return { data, error, loading, refreshing, reload, refresh };
}
