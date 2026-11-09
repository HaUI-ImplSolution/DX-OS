// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { listFamilies, type Family } from "./api";

// Families fetched once for every form that opens.
let familiesCache: Promise<Family[]> | null = null;
export function useFamilies(): Family[] {
  const [families, setFamilies] = useState<Family[]>([]);
  useEffect(() => {
    familiesCache ??= listFamilies().catch(() => {
      familiesCache = null;
      return [];
    });
    let current = true;
    familiesCache.then((loaded) => current && setFamilies(loaded));
    return () => {
      current = false;
    };
  }, []);
  return families;
}
