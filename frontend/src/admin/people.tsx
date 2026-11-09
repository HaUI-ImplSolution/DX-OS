// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useState } from "react";
import { useInput, useRecordContext } from "react-admin";
import { Autocomplete, CircularProgress, TextField as MuiTextField } from "@mui/material";
import { peopleByIds, searchPeople } from "./api";
import type { Person } from "./types";

const MIN_SEARCH = 2;
const MAX_IDS = 50;
const SEARCH_DELAY_MS = 300;
const BATCH_DELAY_MS = 20;

// Names already looked up; null is an id nobody holds.
const names = new Map<string, string | null>();
const waiting = new Map<string, Array<(name: string | null) => void>>();
let batchTimer: ReturnType<typeof setTimeout> | undefined;

async function flush() {
  batchTimer = undefined;
  const ids = [...waiting.keys()];
  for (let from = 0; from < ids.length; from += MAX_IDS) {
    const chunk = ids.slice(from, from + MAX_IDS);
    let found: Person[] = [];
    try {
      found = await peopleByIds(chunk);
    } catch {
      for (const id of chunk) {
        for (const done of waiting.get(id) ?? []) done(null);
        waiting.delete(id);
      }
      continue;
    }
    for (const id of chunk) {
      const name = found.find((person) => person.id === id)?.name ?? null;
      names.set(id, name);
      for (const done of waiting.get(id) ?? []) done(name);
      waiting.delete(id);
    }
  }
}

// One request for all the names a page asks for within a moment.
function nameOf(id: string): Promise<string | null> {
  if (names.has(id)) return Promise.resolve(names.get(id) ?? null);
  return new Promise((resolve) => {
    waiting.set(id, [...(waiting.get(id) ?? []), resolve]);
    if (batchTimer === undefined) batchTimer = setTimeout(flush, BATCH_DELAY_MS);
  });
}

function useName(id: string | null | undefined) {
  const [name, setName] = useState<string | null | undefined>(id ? names.get(id) : null);
  useEffect(() => {
    if (!id) {
      setName(null);
      return;
    }
    let live = true;
    void nameOf(id).then((found) => live && setName(found));
    return () => {
      live = false;
    };
  }, [id]);
  return name;
}

// The names of several accounts at once, by id; an id nobody holds has no entry.
export function useNames(ids: string[]): Record<string, string> {
  const key = [...new Set(ids)].sort().join(",");
  const [found, setFound] = useState<Record<string, string>>({});
  useEffect(() => {
    let live = true;
    const wanted = key ? key.split(",") : [];
    void Promise.all(wanted.map(async (id) => [id, await nameOf(id)] as const)).then((pairs) => {
      if (live) setFound(Object.fromEntries(pairs.filter((pair): pair is [string, string] => pair[1] !== null)));
    });
    return () => {
      live = false;
    };
  }, [key]);
  return found;
}

// The display name of the user a record holds in a user field.
export function PersonField({ source, label }: { source: string; label?: string }) {
  void label;
  const record = useRecordContext<Record<string, unknown>>();
  const id = typeof record?.[source] === "string" ? (record[source] as string) : null;
  const name = useName(id);
  if (!id) return <span>—</span>;
  return <span>{name ?? id}</span>;
}

// Choose a user by name; the record keeps the account id.
export function PersonInput({ source, label, required }: { source: string; label: string; required?: boolean }) {
  const { field, fieldState } = useInput({ source });
  const id: string | null = field.value ? String(field.value) : null;
  const heldName = useName(id);
  const [text, setText] = useState("");
  const [found, setFound] = useState<Person[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const query = text.trim();
    if (query.length < MIN_SEARCH) {
      setFound([]);
      return;
    }
    let live = true;
    setLoading(true);
    const timer = setTimeout(() => {
      searchPeople(query)
        .then((people) => live && setFound(people))
        .catch(() => live && setFound([]))
        .finally(() => live && setLoading(false));
    }, SEARCH_DELAY_MS);
    return () => {
      live = false;
      clearTimeout(timer);
      setLoading(false);
    };
  }, [text]);

  const current: Person | null = useMemo(() => (id ? { id, name: heldName ?? id } : null), [id, heldName]);
  const options = useMemo(
    () => (current && !found.some((person) => person.id === current.id) ? [current, ...found] : found),
    [current, found],
  );

  return (
    <Autocomplete<Person>
      fullWidth
      options={options}
      value={current}
      loading={loading}
      filterOptions={(all) => all}
      getOptionLabel={(person) => person.name}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      onInputChange={(_, value, reason) => reason === "input" && setText(value)}
      onChange={(_, person) => field.onChange(person?.id ?? null)}
      noOptionsText={text.trim().length < MIN_SEARCH ? "Gõ ít nhất 2 ký tự của tên" : "Không tìm thấy ai"}
      loadingText="Đang tìm…"
      clearText="Bỏ chọn"
      renderInput={(params) => (
        <MuiTextField
          {...params}
          label={label}
          required={required}
          size="small"
          error={fieldState.invalid}
          helperText={fieldState.error?.message}
          slotProps={{
            input: {
              ...params.InputProps,
              endAdornment: (
                <>
                  {loading ? <CircularProgress size={16} /> : null}
                  {params.InputProps.endAdornment}
                </>
              ),
            },
          }}
        />
      )}
    />
  );
}
