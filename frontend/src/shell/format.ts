// SPDX-License-Identifier: Apache-2.0
import { getLocale } from "./locale";
import { t } from "./t";

// The locale tag for dates and numbers in the current language.
export const intlLocale = () => (getLocale() === "en" ? "en-GB" : "vi-VN");

// Dates as the reader expects them: 14:05 03/11/2026, and "2 giờ trước" next to them.
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "";
  return new Date(value).toLocaleString(intlLocale(), {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function timeAgo(value: string | null | undefined, now = Date.now()): string {
  if (!value) return "";
  const seconds = Math.max(0, Math.round((now - new Date(value).getTime()) / 1000));
  if (seconds < 60) return t("vừa xong");
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return t("{n} phút trước", { n: minutes });
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return t("{n} giờ trước", { n: hours });
  const days = Math.floor(hours / 24);
  if (days < 30) return t("{n} ngày trước", { n: days });
  return formatDateTime(value);
}

// A length of time as "3 ngày 4 giờ" or "25 phút": the two largest units, seconds only under a minute.
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.round(totalSeconds));
  if (seconds < 60) return t("{n} giây", { n: seconds });
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return t("{n} phút", { n: minutes });
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return minutes % 60 ? t("{h} giờ {m} phút", { h: hours, m: minutes % 60 }) : t("{n} giờ", { n: hours });
  const days = Math.floor(hours / 24);
  return hours % 24 ? t("{d} ngày {h} giờ", { d: days, h: hours % 24 }) : t("{n} ngày", { n: days });
}
