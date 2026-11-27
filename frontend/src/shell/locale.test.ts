// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { beforeEach, describe, expect, it, vi } from "vitest";

// The setup file replaces the language module for the other tests; this file tests the real one.
vi.unmock("./locale");

const reload = vi.fn();
const root = { lang: "" };

function browser(stored: string | null, blocked = false) {
  const store = new Map<string, string>(stored === null ? [] : [["dxos.locale", stored]]);
  vi.stubGlobal("window", {
    localStorage: {
      getItem: (key: string) => {
        if (blocked) throw new Error("blocked");
        return store.get(key) ?? null;
      },
      setItem: (key: string, value: string) => {
        if (blocked) throw new Error("blocked");
        store.set(key, value);
      },
    },
    location: { reload },
  });
  vi.stubGlobal("document", { documentElement: root });
  return store;
}

async function load() {
  vi.resetModules();
  return import("./locale");
}

beforeEach(() => {
  reload.mockClear();
  root.lang = "";
});

describe("the language of the interface", () => {
  it("starts in Vietnamese and sets the page language", async () => {
    browser(null);
    const locale = await load();
    expect(locale.getLocale()).toBe("vi");
    expect(root.lang).toBe("vi");
  });

  it("starts in the language kept in the browser", async () => {
    browser("en");
    const locale = await load();
    expect(locale.getLocale()).toBe("en");
    expect(root.lang).toBe("en");
  });

  it("ignores a kept value that is not a language", async () => {
    browser("fr");
    expect((await load()).getLocale()).toBe("vi");
  });

  it("keeps the default when the storage is blocked", async () => {
    browser(null, true);
    const locale = await load();
    expect(locale.getLocale()).toBe("vi");
    locale.setLocale("en");
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("keeps the new language and reloads the page", async () => {
    const store = browser(null);
    const locale = await load();
    locale.setLocale("en");
    expect(store.get("dxos.locale")).toBe("en");
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("does nothing when the language is the one in use", async () => {
    const store = browser(null);
    const locale = await load();
    locale.setLocale("vi");
    expect(store.size).toBe(0);
    expect(reload).not.toHaveBeenCalled();
  });
});

describe("unsaved work", () => {
  it("asks the screen that registered a check", async () => {
    browser(null);
    const locale = await load();
    expect(locale.hasUnsavedWork()).toBe(false);
    let dirty = true;
    const remove = locale.guardUnsavedWork(() => dirty);
    expect(locale.hasUnsavedWork()).toBe(true);
    dirty = false;
    expect(locale.hasUnsavedWork()).toBe(false);
    remove();
    expect(locale.hasUnsavedWork()).toBe(false);
  });

  it("does not remove the check of a later screen", async () => {
    browser(null);
    const locale = await load();
    const removeFirst = locale.guardUnsavedWork(() => false);
    locale.guardUnsavedWork(() => true);
    removeFirst();
    expect(locale.hasUnsavedWork()).toBe(true);
  });
});
