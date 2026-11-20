// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
// Everything the store needs from DX-OS: file a return or a warranty claim, and read what DX-OS sends back.
import { createHmac, timingSafeEqual } from 'node:crypto';

const BASE = (process.env.DXOS_BASE_URL || '').replace(/\/$/, '');
// One client per model: the role CUSTOMER of retail and the role REQUESTER of warranty.
const CLIENTS = {
  retail: { id: process.env.DXOS_CUSTOMER_CLIENT_ID, secret: process.env.DXOS_CUSTOMER_CLIENT_SECRET },
  warranty: { id: process.env.DXOS_REQUESTER_CLIENT_ID, secret: process.env.DXOS_REQUESTER_CLIENT_SECRET },
};
const tokens = new Map();
const productIds = new Map();

async function token(model) {
  const cached = tokens.get(model);
  if (cached && cached.expires > Date.now() + 30000) return cached.value;
  const client = CLIENTS[model];
  if (!BASE || !client?.id || !client?.secret) throw new Error('DX-OS is not connected: run dxos connect, see README.md');
  const response = await fetch(`${BASE}/realms/dxos/protocol/openid-connect/token`, {
    method: 'POST',
    body: new URLSearchParams({ grant_type: 'client_credentials', client_id: client.id, client_secret: client.secret }),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || !body.access_token) throw new Error(`DX-OS refused the client ${client.id}`);
  tokens.set(model, { value: body.access_token, expires: Date.now() + body.expires_in * 1000 });
  return body.access_token;
}

async function call(model, method, path, payload) {
  const response = await fetch(`${BASE}/api/v1/data/${model}/${path}`, {
    method,
    headers: { Authorization: `Bearer ${await token(model)}`, 'Content-Type': 'application/json' },
    body: payload ? JSON.stringify(payload) : undefined,
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`DX-OS answered ${response.status}: ${body.message || 'no message'}`);
  return body.data;
}

async function productId(code) {
  if (!productIds.has(code)) {
    const found = await call('retail', 'GET', `product?filter.code=${encodeURIComponent(code)}`);
    if (!found?.items?.length) throw new Error(`the product ${code} is not in DX-OS: run setup/setup.sh`);
    productIds.set(code, found.items[0].id);
  }
  return productIds.get(code);
}

const record = (data) => ({ id: data.id, status: data.status, webUrl: data.webUrl || null });

// The customer has no account, so the clerk files for them and the name goes into the reason.
export async function fileReturn(order, { kind, quantity, reason }) {
  const data = await call('retail', 'POST', 'return_request', {
    product: await productId(order.product_code),
    receipt: order.receipt,
    kind,
    quantity,
    reason: `Khách ${order.customer}: ${reason}`,
  });
  return record(data);
}

export async function fileWarranty(order, { serial, detail }) {
  const data = await call('warranty', 'POST', 'ticket', {
    title: order.product_name,
    detail,
    priority: 'Trung bình',
    serial,
    purchased_on: order.sold_on,
    receipt: order.receipt,
    customer: order.customer,
  });
  return record(data);
}

// True when the signature header is the HMAC-SHA256 of the raw body with the shared secret.
export function signatureValid(rawBody, header) {
  const secret = process.env.DXOS_WEBHOOK_SECRET;
  if (!secret || !header) return false;
  const expected = Buffer.from(`sha256=${createHmac('sha256', secret).update(rawBody).digest('hex')}`);
  const given = Buffer.from(header);
  return given.length === expected.length && timingSafeEqual(given, expected);
}
