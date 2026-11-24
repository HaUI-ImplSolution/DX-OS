// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import type { Family } from "./api";
import { asList, list, mentions, rec, text, texts, type Rec } from "./formParts";
import { processRows } from "./processModel";
import { t } from "../shell/t";

const nameOf = (entities: Rec, key: string) => text(rec(entities[key]).label) || key;

// The places of one entity that use one of its fields.
export function fieldUses(entity: Rec, field: string, family: Family | undefined): string[] {
  const uses: string[] = [];
  if (entity.owner_via === field) uses.push(t("Kế thừa quyền từ hồ sơ liên quan"));
  Object.entries(rec(entity.fields)).forEach(([name, raw]) => {
    if (name !== field && mentions(rec(raw).required_when, field)) uses.push(t("Trường dữ liệu {name}: điều kiện bắt buộc", { name }));
  });
  if (entity.process !== undefined) {
    const process = rec(entity.process);
    if (Object.values(rec(process.booking)).includes(field)) uses.push(t("Quy trình: cấu hình đặt chỗ"));
    Object.entries(rec(process.after)).forEach(([state, items]) => {
      if (list(items).some((item) => rec(item).user === field || rec(item).group_from === field)) {
        uses.push(t("Quy trình: hành động hoàn tất ở trạng thái {state}", { state }));
      }
    });
    processRows(process, family).actions
      .filter((action) => !action.removed)
      .forEach((action) => {
        if (action.require.includes(field)) uses.push(t("Hành động {name}: bắt buộc điền", { name: action.name }));
        if (mentions(action.when, field)) uses.push(t("Hành động {name}: điều kiện thực hiện", { name: action.name }));
      });
  }
  Object.entries(rec(rec(entity.ai).tasks)).forEach(([name, raw]) => {
    const task = rec(raw);
    if (texts(task.input_fields).includes(field) || task.output_field === field) uses.push(t("Trợ lý nghiệp vụ: nhiệm vụ {name}", { name }));
  });
  const ui = rec(entity.ui);
  const listView = rec(ui.list);
  if (texts(listView.columns).includes(field) || text(listView.sort).replace(/^-/, "") === field) uses.push(t("Giao diện: cột hoặc cách sắp xếp của danh sách"));
  if (list(rec(ui.form).groups).some((group) => texts(rec(group).fields).includes(field))) uses.push(t("Giao diện: nhóm trường trên form"));
  if (field in rec(ui.hints)) uses.push(t("Giao diện: gợi ý của trường"));
  const placeholder = `{{${field}}}`;
  Object.entries(rec(entity.documents)).forEach(([name, raw]) => {
    const document = rec(raw);
    const written = [text(document.file), text(document.title)];
    const named = list(document.body).some((block) => {
      const item = rec(block);
      written.push(text(item.heading), text(item.text));
      return texts(item.fields).includes(field);
    });
    if (named || written.some((line) => line.includes(placeholder))) uses.push(t("Mẫu in {name}", { name }));
  });
  return uses;
}

// The places of the whole model that point at one entity.
export function entityUses(entities: Rec, key: string): string[] {
  const uses: string[] = [];
  Object.entries(entities).forEach(([other, raw]) => {
    if (other === key) return;
    Object.entries(rec(rec(raw).fields)).forEach(([field, spec]) => {
      if (rec(spec).to === key) uses.push(t("{entity}: trường {field} tham chiếu tới", { entity: nameOf(entities, other), field }));
    });
  });
  return uses;
}

// The places of the whole model that name one role.
export function roleUses(entities: Rec, role: string): string[] {
  const uses: string[] = [];
  Object.keys(entities).forEach((key) => {
    const entity = rec(entities[key]);
    const name = nameOf(entities, key);
    if (role in rec(entity.access)) uses.push(t("{entity}: quyền truy cập", { entity: name }));
    if (entity.process === undefined) return;
    const process = rec(entity.process);
    if (asList(process.handler).includes(role)) uses.push(t("{entity}: vai trò xử lý", { entity: name }));
    Object.entries(rec(process.actions)).forEach(([action, spec]) => {
      if (asList(rec(spec).role).includes(role)) uses.push(t("{entity}: hành động {action}", { entity: name, action }));
    });
    if (list(process.escalate).some((level) => asList(rec(level).to).includes(role))) uses.push(t("{entity}: báo khi quá hạn", { entity: name }));
  });
  return uses;
}

// The places the form follows when a role is renamed: the access of every entity and its handlers.
export function renameRoleIn(entities: Rec, from: string, to: string): Rec {
  const renamed: Rec = {};
  Object.entries(entities).forEach(([key, raw]) => {
    const item = { ...rec(raw) };
    const access = rec(item.access);
    if (from in access) item.access = Object.fromEntries(Object.entries(access).map(([name, scope]) => [name === from ? to : name, scope]));
    if (item.process !== undefined) {
      const process = { ...rec(item.process) };
      const handler = process.handler;
      if (handler === from) process.handler = to;
      else if (Array.isArray(handler)) process.handler = handler.map((name) => (name === from ? to : name));
      item.process = process;
    }
    renamed[key] = item;
  });
  return renamed;
}
