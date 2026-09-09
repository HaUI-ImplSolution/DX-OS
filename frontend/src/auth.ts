import type { LoginResponse } from "./types";

const STORAGE_KEY = "dxfarm.auth";

export type CurrentUser = LoginResponse;

export function getCurrentUser(): CurrentUser | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as CurrentUser;
  } catch {
    return null;
  }
}

export function getToken(): string | null {
  return getCurrentUser()?.token ?? null;
}

export function setCurrentUser(user: CurrentUser) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
}

export function clearCurrentUser() {
  localStorage.removeItem(STORAGE_KEY);
}
