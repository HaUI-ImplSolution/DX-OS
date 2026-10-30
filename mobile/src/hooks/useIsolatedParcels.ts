// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { listIsolatedSeasons } from "@/api/endpoints";

// Codes of the parcels whose active season is under isolation; a failure leaves the set empty.
export function useIsolatedParcels(version: number): ReadonlySet<string> {
  const [codes, setCodes] = useState<ReadonlySet<string>>(new Set());
  useEffect(() => {
    let cancelled = false;
    listIsolatedSeasons()
      .then((statuses) => {
        if (!cancelled) setCodes(new Set(statuses.map((status) => status.parcelCode)));
      })
      .catch(() => {
        if (!cancelled) setCodes(new Set());
      });
    return () => {
      cancelled = true;
    };
  }, [version]);
  return codes;
}
