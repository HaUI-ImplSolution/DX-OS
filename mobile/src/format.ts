const pad = (value: number) => String(value).padStart(2, "0");

// Accepts a date-only string (yyyy-mm-dd) or an ISO instant and returns dd/mm/yyyy.
export function formatDate(value: string | null | undefined): string {
  if (!value) return "";
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (dateOnly) return `${dateOnly[3]}/${dateOnly[2]}/${dateOnly[1]}`;
  const date = new Date(value);
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
}

export function formatDateTime(value: string): string {
  const date = new Date(value);
  return `${pad(date.getHours())}:${pad(date.getMinutes())} ${formatDate(value)}`;
}
