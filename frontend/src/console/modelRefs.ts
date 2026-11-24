// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import type { Family } from "./api";
import { asList, list, mentions, rec, renameKey, text, texts, type Rec } from "./formParts";
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
      written.push(text(item.heading), text(item.text), ...texts(item.signatures));
      return texts(item.fields).includes(field);
    });
    if (named || written.some((line) => line.includes(placeholder))) uses.push(t("Mẫu in {name}", { name }));
  });
  return uses;
}

const swap = (value: unknown, from: string, to: string) => (value === from ? to : value);
// A name, or a list of names, with one name replaced; a single name stays a single name.
const swapIn = (value: unknown, from: string, to: string) => (Array.isArray(value) ? value.map((item) => swap(item, from, to)) : swap(value, from, to));
const inText = (value: unknown, from: string, to: string) => (typeof value === "string" ? value.split(`{{${from}}}`).join(`{{${to}}}`) : value);

// A condition is written `field operator value`, joined by and, or, not and brackets.
// Only the word before an operator is a field, so a value or a quoted text that equals the name stays as it is.
export function renameInCondition(condition: unknown, from: string, to: string): unknown {
  if (typeof condition !== "string") return condition;
  const tokens = [...condition.matchAll(/"[^"]*"|'[^']*'|==|!=|<=|>=|<|>|[()]|[^\s()=!<>"']+/g)];
  let written = "";
  let last = 0;
  tokens.forEach((token, at) => {
    if (token[0] === from && /^(==|!=|<=|>=|<|>)$/.test(tokens[at + 1]?.[0] ?? "")) {
      written += condition.slice(last, token.index) + to;
      last = (token.index ?? 0) + token[0].length;
    }
  });
  return written + condition.slice(last);
}

// The changes to one entity when a field is renamed: every place fieldUses lists follows the new name.
export function renameFieldIn(entity: Rec, from: string, to: string, family: Family | undefined): Rec {
  const fields = Object.fromEntries(
    Object.entries(rec(entity.fields)).map(([name, raw]) => {
      const spec = rec(raw);
      return [name, spec.required_when === undefined ? raw : { ...spec, required_when: renameInCondition(spec.required_when, from, to) }];
    }),
  );
  const next: Rec = { fields: renameKey(fields, from, to) };
  if (entity.owner_via === from) next.owner_via = to;
  if (entity.process) {
    const process = { ...rec(entity.process) };
    const booking = rec(process.booking);
    if (Object.keys(booking).length > 0) process.booking = Object.fromEntries(Object.entries(booking).map(([key, value]) => [key, swap(value, from, to)]));
    const after = rec(process.after);
    if (Object.keys(after).length > 0) {
      process.after = Object.fromEntries(
        Object.entries(after).map(([state, items]) => [
          state,
          list(items).map((item) => ({ ...rec(item), ...Object.fromEntries(["user", "group_from"].filter((key) => rec(item)[key] === from).map((key) => [key, to])) })),
        ]),
      );
    }
    // An action of the template that requires the field or waits on it is written into the file with the new name.
    const actions = { ...rec(process.actions) };
    processRows(process, family).actions
      .filter((action) => !action.removed)
      .forEach((action) => {
        const spec = { ...rec(actions[action.name]) };
        const when = renameInCondition(typeof spec.when === "string" ? spec.when : action.when, from, to);
        const waits = when !== (typeof spec.when === "string" ? spec.when : action.when);
        const requires = action.require.includes(from);
        if (!waits && !requires) return;
        if (requires) spec.require = action.require.map((name) => swap(name, from, to));
        if (waits) spec.when = when;
        actions[action.name] = spec;
      });
    if (Object.keys(actions).length > 0) process.actions = actions;
    next.process = process;
  }
  const ai = rec(entity.ai);
  const tasks = rec(ai.tasks);
  if (Object.keys(tasks).length > 0) {
    next.ai = {
      ...ai,
      tasks: Object.fromEntries(
        Object.entries(tasks).map(([name, raw]) => {
          const task = rec(raw);
          return [name, { ...task, input_fields: texts(task.input_fields).map((item) => swap(item, from, to)), output_field: swap(task.output_field, from, to) }];
        }),
      ),
    };
  }
  if (entity.ui !== undefined) {
    const ui = { ...rec(entity.ui) };
    if (ui.list !== undefined) {
      const view = { ...rec(ui.list) };
      if (view.columns !== undefined) view.columns = texts(view.columns).map((column) => swap(column, from, to));
      if (typeof view.sort === "string") view.sort = view.sort.replace(/^(-?)(.+)$/, (_all, sign: string, column: string) => `${sign}${swap(column, from, to)}`);
      ui.list = view;
    }
    if (ui.form !== undefined) {
      ui.form = { ...rec(ui.form), groups: list(rec(ui.form).groups).map((group) => ({ ...rec(group), fields: texts(rec(group).fields).map((item) => swap(item, from, to)) })) };
    }
    if (from in rec(ui.hints)) ui.hints = renameKey(rec(ui.hints), from, to);
    next.ui = ui;
  }
  if (entity.documents !== undefined) {
    next.documents = Object.fromEntries(
      Object.entries(rec(entity.documents)).map(([name, raw]) => {
        const document = rec(raw);
        const body = list(document.body).map((block) => {
          const item = rec(block);
          return Object.fromEntries(
            Object.entries(item).map(([key, value]) => [
              key,
              key === "fields" ? texts(value).map((field) => swap(field, from, to)) : key === "signatures" ? texts(value).map((line) => inText(line, from, to)) : inText(value, from, to),
            ]),
          );
        });
        return [name, { ...document, file: inText(document.file, from, to), title: inText(document.title, from, to), ...(document.body === undefined ? {} : { body }) }];
      }),
    );
  }
  return next;
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

// The places the form follows when a role is renamed: the access of every entity, its handlers, the roles of its actions and who is told when it is overdue.
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
      const actions = rec(process.actions);
      if (Object.keys(actions).length > 0) {
        process.actions = Object.fromEntries(Object.entries(actions).map(([name, spec]) => [name, rec(spec).role === undefined ? spec : { ...rec(spec), role: swapIn(rec(spec).role, from, to) }]));
      }
      if (Array.isArray(process.escalate)) {
        process.escalate = process.escalate.map((level) => (rec(level).to === undefined ? level : { ...rec(level), to: swapIn(rec(level).to, from, to) }));
      }
      item.process = process;
    }
    renamed[key] = item;
  });
  return renamed;
}
