// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef } from "react";
import { useRefresh } from "react-admin";
import { useLocation } from "react-router-dom";
import { onLiveChange } from "../shell/liveChanges";

const PAGES_WITHOUT_RECORD = ["stats", "calendar", "console"];

// A form being typed into is left alone: the create form, and the edit form of a record.
function isForm(pathname: string) {
  const parts = pathname.split("/").filter(Boolean);
  return parts[parts.length - 1] === "create" || (parts.length === 2 && !PAGES_WITHOUT_RECORD.includes(parts[0]));
}

// Reads the list or the record on screen again when a record changes anywhere.
export default function LiveRefresh() {
  const refresh = useRefresh();
  const { pathname } = useLocation();
  const current = useRef(pathname);
  current.current = pathname;

  useEffect(
    () =>
      onLiveChange(() => {
        if (!isForm(current.current)) refresh();
      }),
    [refresh],
  );
  return null;
}
