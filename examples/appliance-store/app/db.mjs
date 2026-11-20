// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
// The database of the store: products, orders, staff, and the claims filed in DX-OS.
import { DatabaseSync } from 'node:sqlite';
import { randomBytes, scryptSync } from 'node:crypto';
import { readFileSync } from 'node:fs';

export function hashPassword(password, salt) {
  return scryptSync(password, salt, 32).toString('hex');
}

// Opens the file, creates the tables on the first start and loads the catalog and the sample sales.
export function openDatabase(file, catalogFile) {
  const db = new DatabaseSync(file);
  db.exec(`
    PRAGMA journal_mode = WAL;
    CREATE TABLE IF NOT EXISTS products (code TEXT PRIMARY KEY, name TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS orders (
      id INTEGER PRIMARY KEY, receipt TEXT NOT NULL UNIQUE, customer TEXT NOT NULL,
      product_code TEXT NOT NULL REFERENCES products(code), quantity INTEGER NOT NULL, sold_on TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS staff (username TEXT PRIMARY KEY, salt TEXT NOT NULL, hash TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS sessions (token TEXT PRIMARY KEY, username TEXT NOT NULL, created_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS claims (
      id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL REFERENCES orders(id),
      kind TEXT NOT NULL CHECK (kind IN ('return', 'warranty')), model TEXT NOT NULL, dxos_id INTEGER NOT NULL,
      status TEXT NOT NULL, overdue INTEGER NOT NULL DEFAULT 0, web_url TEXT, summary TEXT NOT NULL,
      last_event INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, UNIQUE (model, dxos_id));
    CREATE TABLE IF NOT EXISTS seen_events (event_id INTEGER PRIMARY KEY);
    CREATE TABLE IF NOT EXISTS unknown_events (event_id INTEGER PRIMARY KEY, first_seen INTEGER NOT NULL);
  `);
  const insertProduct = db.prepare('INSERT OR IGNORE INTO products (code, name) VALUES (?, ?)');
  for (const line of readFileSync(catalogFile, 'utf8').split('\n')) {
    const [code, ...name] = line.split('\t');
    if (code && name.length) insertProduct.run(code.trim(), name.join('\t').trim());
  }
  if (db.prepare('SELECT COUNT(*) AS n FROM orders').get().n === 0) {
    const today = new Date();
    const daysAgo = (n) => new Date(today - n * 86400000).toISOString().slice(0, 10);
    const insertOrder = db.prepare(
      'INSERT INTO orders (receipt, customer, product_code, quantity, sold_on) VALUES (?, ?, ?, ?, ?)');
    [['HD-1001', 'Nguyễn Văn An', 'TV-55', 1, 9], ['HD-1002', 'Trần Thị Bình', 'FR-250', 1, 20],
      ['HD-1003', 'Lê Quang Cường', 'WM-90', 1, 45], ['HD-1004', 'Phạm Thu Dung', 'AC-12', 2, 3],
      ['HD-1005', 'Hoàng Minh Đức', 'FAN-01', 3, 120], ['HD-1006', 'Vũ Lan Hương', 'TV-55', 1, 300],
    ].forEach(([receipt, customer, code, quantity, ago]) => insertOrder.run(receipt, customer, code, quantity, daysAgo(ago)));
  }
  return db;
}

// Creates the sign-in of the clerk once; the password is printed on that first start only.
export function ensureStaff(db, username, password) {
  if (db.prepare('SELECT 1 FROM staff WHERE username = ?').get(username)) return null;
  const given = password || randomBytes(9).toString('base64url');
  const salt = randomBytes(16).toString('hex');
  db.prepare('INSERT INTO staff (username, salt, hash) VALUES (?, ?, ?)').run(username, salt, hashPassword(given, salt));
  return given;
}
