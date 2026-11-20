// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
// The pages of the store as plain HTML.

let linked = false;
export const showClaims = (value) => { linked = value; };

export const esc = (value) =>
  String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const STATUS = {
  return: { submitted: 'Chờ duyệt', approved: 'Đã duyệt', rejected: 'Bị từ chối', returned: 'Cần bổ sung' },
  warranty: { submitted: 'Mới tiếp nhận', in_progress: 'Đang xử lý', resolved: 'Đã xử lý', rejected: 'Từ chối', closed: 'Đã đóng' },
};
const KIND = { return: 'Đổi trả', warranty: 'Bảo hành' };
export const statusLabel = (claim) => STATUS[claim.kind]?.[claim.status] || claim.status;

const page = (title, body, user) => `<!doctype html>
<html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)} · Điện máy Minh Phát</title>
<style>
  body { font: 16px/1.5 system-ui, sans-serif; margin: 0; color: #1d2733; background: #f5f7fa; }
  header { background: #12355b; color: #fff; padding: 12px 24px; display: flex; gap: 24px; align-items: center; }
  header a { color: #fff; text-decoration: none; } header .grow { flex: 1; font-weight: 600; }
  main { max-width: 920px; margin: 24px auto; padding: 0 16px; }
  table { width: 100%; border-collapse: collapse; background: #fff; } th, td { padding: 10px 12px; border-bottom: 1px solid #e3e8ee; text-align: left; }
  .card { box-sizing: border-box; min-width: 0; background: #fff; padding: 16px 20px; margin-bottom: 16px; border: 1px solid #e3e8ee; border-radius: 8px; }
  label { display: block; margin: 8px 0 2px; font-weight: 600; } input, select, textarea { width: 100%; padding: 8px; box-sizing: border-box; font: inherit; }
  button { margin-top: 12px; padding: 8px 16px; font: inherit; background: #12355b; color: #fff; border: 0; border-radius: 6px; cursor: pointer; }
  .tag { display: inline-block; padding: 2px 8px; border-radius: 10px; background: #e3ecf7; font-size: 14px; }
  .late { background: #fde2e1; color: #9b1c1c; } .error { background: #fde2e1; padding: 10px 14px; border-radius: 6px; margin-bottom: 16px; }
  .ok { background: #dff3e4; padding: 10px 14px; border-radius: 6px; margin-bottom: 16px; }
  .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; } @media (max-width: 700px) { .cols { grid-template-columns: 1fr; } }
</style></head><body>
<header><span class="grow">Điện máy Minh Phát</span>${user
    ? `<a href="/orders">Đơn bán</a>${linked ? '<a href="/claims">Yêu cầu đang xử lý</a>' : ''}
<form method="post" action="/logout" style="margin:0"><button style="margin:0;background:#1f4f86">Đăng xuất (${esc(user)})</button></form>` : ''}</header>
<main>${body}</main></body></html>`;

const notice = (flash) =>
  flash ? `<div class="${flash.error ? 'error' : 'ok'}">${esc(flash.text)}</div>` : '';

export const loginPage = (flash) => page('Đăng nhập', `${notice(flash)}
<div class="card"><h2>Đăng nhập nhân viên</h2>
<form method="post" action="/login"><label for="u">Tên đăng nhập</label><input id="u" name="username" autocomplete="username" required>
<label for="p">Mật khẩu</label><input id="p" name="password" type="password" autocomplete="current-password" required>
<button>Đăng nhập</button></form></div>`);

export const ordersPage = (user, orders, flash) => page('Đơn bán', `${notice(flash)}<h2>Đơn bán</h2>
<table><tr><th>Hóa đơn</th><th>Khách hàng</th><th>Sản phẩm</th><th>SL</th><th>Ngày mua</th></tr>
${orders.map((o) => `<tr><td><a href="/orders/${o.id}">${esc(o.receipt)}</a></td><td>${esc(o.customer)}</td><td>${esc(o.product_name)}</td><td>${o.quantity}</td><td>${esc(o.sold_on)}</td></tr>`).join('')}
</table>`, user);

const claimRow = (c, withOrder) => `<tr>${withOrder ? `<td><a href="/orders/${c.order_id}">${esc(c.receipt)}</a></td>` : ''}
<td>${KIND[c.kind]}</td><td><span class="tag">${esc(statusLabel(c))}</span>${c.overdue ? ' <span class="tag late">Quá hạn</span>' : ''}</td>
<td>${esc(c.summary)}</td><td>${c.web_url ? `<a href="${esc(c.web_url)}" target="_blank" rel="noopener">Mở trên DX-OS</a>` : ''}</td></tr>`;

export const orderPage = (user, order, claims, flash) => page(`Đơn ${order.receipt}`, `${notice(flash)}
<h2>Đơn ${esc(order.receipt)}</h2>
<div class="card"><b>${esc(order.customer)}</b> · ${esc(order.product_name)} × ${order.quantity} · mua ngày ${esc(order.sold_on)}</div>
${linked ? `<div class="cols">
<div class="card"><h3>Đổi trả</h3><form method="post" action="/orders/${order.id}/return">
<label for="k">Yêu cầu</label><select id="k" name="kind"><option>Đổi hàng</option><option>Trả hàng</option></select>
<label for="q">Số lượng</label><input id="q" name="quantity" type="number" min="1" max="${order.quantity}" value="1" required>
<label for="r">Lý do</label><textarea id="r" name="reason" rows="3" required></textarea>
<button>Gửi yêu cầu đổi trả</button></form></div>
<div class="card"><h3>Bảo hành</h3><form method="post" action="/orders/${order.id}/warranty">
<label for="s">Số seri</label><input id="s" name="serial" required>
<label for="d">Lỗi gặp phải</label><textarea id="d" name="detail" rows="3" required></textarea>
<button>Gửi yêu cầu bảo hành</button></form></div></div>` : '<div class="card">Ứng dụng chưa có quy trình đổi trả và bảo hành.</div>'}
${linked ? `<h3>Yêu cầu của đơn này</h3>
${claims.length ? `<table><tr><th>Loại</th><th>Trạng thái</th><th>Nội dung</th><th></th></tr>${claims.map((c) => claimRow(c, false)).join('')}</table>` : '<p>Chưa có yêu cầu nào.</p>'}` : ''}`, user);

export const claimsPage = (user, claims) => page('Yêu cầu đang xử lý', `<h2>Yêu cầu đang xử lý</h2>
${claims.length ? `<table><tr><th>Hóa đơn</th><th>Loại</th><th>Trạng thái</th><th>Nội dung</th><th></th></tr>${claims.map((c) => claimRow(c, true)).join('')}</table>` : '<p>Chưa có yêu cầu nào.</p>'}`, user);

export const errorPage = (user, text) => page('Lỗi', `<div class="error">${esc(text)}</div><p><a href="/orders">Về danh sách đơn bán</a></p>`, user);
