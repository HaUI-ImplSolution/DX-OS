// SPDX-License-Identifier: Apache-2.0
// A cell that a spreadsheet would read as a formula is written as text, so a value typed by a person
// cannot run in the program that opens the file.
const FORMULA_START = /^[=+\-@\t\r]/;

export function csvCell(value: string, keepAsIs = false): string {
  const text = !keepAsIs && FORMULA_START.test(value) ? `'${value}` : value;
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

// The file starts with a byte order mark so that Excel reads the Vietnamese text as UTF-8.
export function toCsv(rows: { text: string; keepAsIs?: boolean }[][]): string {
  return "﻿" + rows.map((row) => row.map((cell) => csvCell(cell.text, cell.keepAsIs)).join(",")).join("\r\n") + "\r\n";
}

export function download(name: string, content: string) {
  const url = URL.createObjectURL(new Blob([content], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
