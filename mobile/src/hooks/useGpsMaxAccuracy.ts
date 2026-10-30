// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { getAppManifest } from "@/api/endpoints";
import { GPS_MAX_ACCURACY_METERS } from "@/config";

// Largest GPS error the backend accepts; the built-in value is used until the manifest arrives.
export function useGpsMaxAccuracy(): number {
  const [meters, setMeters] = useState(GPS_MAX_ACCURACY_METERS);
  useEffect(() => {
    let cancelled = false;
    getAppManifest()
      .then((manifest) => {
        const value = Number(manifest.settings?.gpsMaxAccuracyMeters);
        if (!cancelled && Number.isFinite(value) && value > 0) setMeters(value);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);
  return meters;
}
