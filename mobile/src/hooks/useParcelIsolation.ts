import { useEffect, useState } from "react";
import { getIsolationStatus, listCropSeasons } from "@/api/endpoints";

// True when the parcel's active season is under isolation; failures count as not isolated.
export function useParcelIsolation(parcelCode: string, version: number): boolean {
  const [isolated, setIsolated] = useState(false);
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const seasons = await listCropSeasons(parcelCode);
        const active = seasons.find((season) => season.status === "ACTIVE");
        const status = active ? await getIsolationStatus(active.id) : null;
        if (!cancelled) setIsolated(status?.isolated ?? false);
      } catch {
        if (!cancelled) setIsolated(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [parcelCode, version]);
  return isolated;
}
