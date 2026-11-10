// SPDX-License-Identifier: Apache-2.0
import type { Entity } from '../tools/records.js';

const plain = (value: unknown, maximum = 400) => String(value ?? '').replace(/[\x00-\x08\x0b-\x1f\x7f]/g, ' ').trim().slice(0, maximum);
export function date(value: unknown, timeZone: string) {
  const instant = new Date(String(value));
  return Number.isFinite(instant.getTime()) ? new Intl.DateTimeFormat('vi-VN', {
    timeZone, dateStyle: 'short', timeStyle: 'short',
  }).format(instant) : null;
}

export function recordDetails(entity: Entity, record: Record<string, unknown>, timeZone: string) {
  const lines = [plain(entity.label, 120)];
  if (entity.process) lines.push(`Trạng thái: ${entity.process.states.find(state => state.name === record.status)?.label ?? 'Chưa xác định'}`);
  for (const field of entity.fields.slice(0, 12)) {
    const value = record[field.name];
    if (value === null || value === undefined || value === '') continue;
    let formatted: string;
    if (field.type === 'datetime') formatted = date(value, timeZone) ?? 'Chưa xác định';
    else if (['file', 'reference', 'user'].includes(field.type) || typeof value === 'object') formatted = 'Xem trên DX-OS';
    else if (typeof value === 'boolean') formatted = value ? 'Có' : 'Không';
    else formatted = plain(value);
    lines.push(`${plain(field.label, 100)}: ${formatted}`);
  }
  const deadline = date(record.handle_by, timeZone);
  if (deadline) lines.push(`Hạn xử lý: ${deadline}${record.overdue ? ' — Quá hạn' : ''}`);
  lines.push(`Múi giờ: ${timeZone}`);
  return lines.join('\n').slice(0, 3500);
}
