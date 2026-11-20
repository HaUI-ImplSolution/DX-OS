// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
// The store: a sales book for the clerks, with returns and warranty claims that DX-OS runs.
import { createServer } from 'node:http';
import { randomBytes, timingSafeEqual } from 'node:crypto';
import { ensureStaff, hashPassword, openDatabase } from './db.mjs';
import { fileReturn, fileWarranty, signatureValid } from './dxos.mjs';
import { claimsPage, errorPage, loginPage, orderPage, ordersPage } from './views.mjs';

const PORT = Number(process.env.APP_PORT || 3000);
const db = openDatabase(process.env.APP_DB || './store.sqlite', process.env.APP_CATALOG || '../catalog/products.tsv');
const first = ensureStaff(db, 'nhanvien', process.env.APP_STAFF_PASSWORD);
if (first && !process.env.APP_STAFF_PASSWORD) console.log(`The clerk signs in as nhanvien with the password ${first}`);

const SESSION_SECONDS = 8 * 3600;
const FINAL = ['approved', 'rejected', 'resolved', 'closed'];
const ORDER_SQL = `SELECT o.*, p.name AS product_name FROM orders o JOIN products p ON p.code = o.product_code`;
const CLAIM_SQL = `SELECT c.*, o.receipt FROM claims c JOIN orders o ON o.id = c.order_id`;

const send = (res, status, body, headers = {}) => {
  res.writeHead(status, { 'Content-Type': 'text/html; charset=utf-8', ...headers });
  res.end(body);
};
const redirect = (res, to, headers = {}) => { res.writeHead(303, { Location: to, ...headers }); res.end(); };

function readBody(req, limit = 65536) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) { reject(new Error('body too large')); req.destroy(); } else chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

const cookie = (req, name) => (req.headers.cookie || '').split(/;\s*/).map((c) => c.split('=')).find(([k]) => k === name)?.[1];
function sessionUser(req) {
  const token = cookie(req, 'sid');
  const row = token && db.prepare('SELECT username, created_at FROM sessions WHERE token = ?').get(token);
  return row && Date.now() / 1000 - row.created_at < SESSION_SECONDS ? row.username : null;
}

async function login(req, res) {
  const form = new URLSearchParams((await readBody(req)).toString());
  const row = db.prepare('SELECT salt, hash FROM staff WHERE username = ?').get(form.get('username') || '');
  const given = Buffer.from(hashPassword(form.get('password') || '', row?.salt || '00'), 'hex');
  if (!row || !timingSafeEqual(given, Buffer.from(row.hash, 'hex'))) {
    return send(res, 401, loginPage({ error: true, text: 'Sai tên đăng nhập hoặc mật khẩu.' }));
  }
  const token = randomBytes(24).toString('base64url');
  db.prepare('INSERT INTO sessions (token, username, created_at) VALUES (?, ?, ?)').run(token, form.get('username'), Math.floor(Date.now() / 1000));
  redirect(res, '/orders', { 'Set-Cookie': `sid=${token}; HttpOnly; SameSite=Lax; Path=/; Max-Age=${SESSION_SECONDS}` });
}

// A claim is filed in DX-OS first; the store keeps it only once DX-OS has accepted it.
async function file(res, user, order, kind, form) {
  const text = (name) => (form.get(name) || '').trim().slice(0, 500);
  let result;
  let summary;
  try {
    if (kind === 'return') {
      const quantity = Number(form.get('quantity'));
      const wanted = text('kind');
      if (!['Đổi hàng', 'Trả hàng'].includes(wanted) || !Number.isInteger(quantity) || quantity < 1 || quantity > order.quantity || !text('reason')) {
        return send(res, 400, errorPage(user, 'Phiếu đổi trả chưa đúng: chọn yêu cầu, số lượng trong đơn và nêu lý do.'));
      }
      result = await fileReturn(order, { kind: wanted, quantity, reason: text('reason') });
      summary = `${wanted} ${quantity}: ${text('reason')}`;
    } else {
      if (!text('serial') || !text('detail')) {
        return send(res, 400, errorPage(user, 'Phiếu bảo hành cần số seri và lỗi gặp phải.'));
      }
      result = await fileWarranty(order, { serial: text('serial'), detail: text('detail') });
      summary = `Seri ${text('serial')}: ${text('detail')}`;
    }
  } catch (error) {
    console.error(error.message);
    return send(res, 502, errorPage(user, `Chưa gửi được sang DX-OS: ${error.message}`));
  }
  db.prepare(`INSERT INTO claims (order_id, kind, model, dxos_id, status, web_url, summary, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`)
    .run(order.id, kind, kind === 'return' ? 'retail' : 'warranty', result.id, result.status, result.webUrl, summary, new Date().toISOString());
  redirect(res, `/orders/${order.id}`);
}

// What DX-OS sends: every change of the two models, signed, possibly more than once and a little before the claim is saved.
async function webhook(req, res) {
  const raw = await readBody(req);
  if (!signatureValid(raw, req.headers['x-dxos-signature'])) return send(res, 401, 'bad signature', { 'Content-Type': 'text/plain' });
  const event = JSON.parse(raw.toString());
  const done = (status) => { res.writeHead(status); res.end(); };
  const kind = event.model === 'retail' && event.entity === 'return_request' ? 'return'
    : event.model === 'warranty' && event.entity === 'ticket' ? 'warranty' : null;
  if (!kind || db.prepare('SELECT 1 FROM seen_events WHERE event_id = ?').get(event.eventId)) return done(204);
  const claim = db.prepare('SELECT id FROM claims WHERE model = ? AND dxos_id = ?').get(event.model, event.recordId);
  if (!claim) {
    // The claim is saved just after DX-OS answers, so ask for a retry first; a record the store never filed is dropped after two minutes.
    db.prepare('INSERT OR IGNORE INTO unknown_events (event_id, first_seen) VALUES (?, ?)').run(event.eventId, Date.now());
    const seen = db.prepare('SELECT first_seen FROM unknown_events WHERE event_id = ?').get(event.eventId).first_seen;
    if (Date.now() - seen < 120000) return done(503);
    db.prepare('INSERT OR IGNORE INTO seen_events (event_id) VALUES (?)').run(event.eventId);
    return done(204);
  }
  const final = FINAL.includes(event.toStatus);
  db.prepare(`UPDATE claims SET status = COALESCE(?, status), overdue = CASE WHEN ? THEN 1 WHEN ? THEN 0 ELSE overdue END,
              web_url = COALESCE(web_url, ?), last_event = ? WHERE id = ? AND last_event < ?`)
    .run(event.toStatus ?? null, event.action === 'overdue' ? 1 : 0, final ? 1 : 0, event.webUrl ?? null, event.eventId, claim.id, event.eventId);
  db.prepare('INSERT OR IGNORE INTO seen_events (event_id) VALUES (?)').run(event.eventId);
  done(204);
}

async function route(req, res) {
  const url = new URL(req.url, 'http://store');
  const path = url.pathname;
  if (path === '/healthz') return send(res, 200, 'ok', { 'Content-Type': 'text/plain' });
  if (path === '/hooks/dxos' && req.method === 'POST') return webhook(req, res);
  if (path === '/login') return req.method === 'POST' ? login(req, res) : send(res, 200, loginPage());
  const user = sessionUser(req);
  if (!user) return redirect(res, '/login');
  if (path === '/logout' && req.method === 'POST') {
    db.prepare('DELETE FROM sessions WHERE token = ?').run(cookie(req, 'sid'));
    return redirect(res, '/login', { 'Set-Cookie': 'sid=; HttpOnly; Path=/; Max-Age=0' });
  }
  if (path === '/') return redirect(res, '/orders');
  if (path === '/orders') return send(res, 200, ordersPage(user, db.prepare(`${ORDER_SQL} ORDER BY o.sold_on DESC`).all()));
  if (path === '/claims') return send(res, 200, claimsPage(user, db.prepare(`${CLAIM_SQL} ORDER BY c.id DESC`).all()));
  const match = path.match(/^\/orders\/(\d+)(?:\/(return|warranty))?$/);
  const order = match && db.prepare(`${ORDER_SQL} WHERE o.id = ?`).get(Number(match[1]));
  if (order && !match[2] && req.method === 'GET') {
    return send(res, 200, orderPage(user, order, db.prepare(`${CLAIM_SQL} WHERE c.order_id = ? ORDER BY c.id DESC`).all(order.id)));
  }
  if (order && match[2] && req.method === 'POST') {
    return file(res, user, order, match[2], new URLSearchParams((await readBody(req)).toString()));
  }
  send(res, 404, errorPage(user, 'Không tìm thấy trang này.'));
}

createServer((req, res) => {
  route(req, res).catch((error) => {
    console.error(error);
    if (!res.headersSent) send(res, 500, errorPage(null, 'Có lỗi ở ứng dụng.'));
  });
}).listen(PORT, '0.0.0.0', () => console.log(`The store listens on ${PORT}`));
