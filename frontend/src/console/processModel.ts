// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import type { Family } from "./api";
import { asList, conditionOf, rec, text, type Rec } from "./formParts";

// Where a row of a process comes from: the template as it is, the template changed by the file,
// a row the file adds, or a row of the template the file removes.
export type Origin = "base" | "changed" | "added" | "removed";

export interface StateRow {
  name: string;
  label: string;
  terminal: boolean;
  base: boolean;
  removed: boolean;
  origin: Origin;
}

export interface ActionRow {
  name: string;
  label: string;
  from: string[];
  to: string;
  by: "handler" | "owner";
  role: string[];
  needsNote: boolean;
  require: string[];
  when: string;
  base: boolean;
  removed: boolean;
  origin: Origin;
}

const originOf = (base: boolean, spec: unknown): Origin =>
  !base ? "added" : spec === null ? "removed" : spec === undefined ? "base" : "changed";

// The states and actions an entity ends up with: those of its template, changed by what the file declares.
// withdraw and withdrawn belong to every template and are not edited as rows.
export function processRows(process: Rec, family: Family | undefined) {
  const stateSpec = rec(process.states);
  const actionSpec = rec(process.actions);
  const baseStates = (family?.states ?? []).filter((state) => state.name !== "withdrawn");
  const baseActions = (family?.actions ?? []).filter((action) => action.name !== "withdraw");
  const states: StateRow[] = [
    ...baseStates.map((state) => {
      const spec = stateSpec[state.name];
      const changed = rec(spec);
      return {
        name: state.name,
        label: text(changed.label) || state.label,
        terminal: changed.terminal === undefined ? state.terminal : changed.terminal === true,
        base: true,
        removed: spec === null,
        origin: originOf(true, spec),
      };
    }),
    ...Object.keys(stateSpec)
      .filter((name) => name !== "withdrawn" && !baseStates.some((state) => state.name === name))
      .map((name) => ({
        name,
        label: text(rec(stateSpec[name]).label),
        terminal: rec(stateSpec[name]).terminal === true,
        base: false,
        removed: false,
        origin: "added" as Origin,
      })),
  ];
  const actions: ActionRow[] = [
    ...baseActions.map((action) => {
      const spec = actionSpec[action.name];
      const changed = rec(spec);
      return {
        name: action.name,
        label: text(changed.label) || action.label,
        from: changed.from === undefined ? action.from : asList(changed.from),
        to: text(changed.to) || action.to,
        by: (changed.by === undefined ? action.by.toLowerCase() : text(changed.by)) as "handler" | "owner",
        role: changed.role === undefined ? action.roles : asList(changed.role),
        needsNote: changed.needs_note === undefined ? action.needsNote : changed.needs_note === true,
        require: changed.require === undefined ? action.require : asList(changed.require),
        when: changed.when === undefined ? conditionOf(action.when) : text(changed.when),
        base: true,
        removed: spec === null,
        origin: originOf(true, spec),
      };
    }),
    ...Object.keys(actionSpec)
      .filter((name) => name !== "withdraw" && !baseActions.some((action) => action.name === name))
      .map((name) => {
        const changed = rec(actionSpec[name]);
        return {
          name,
          label: text(changed.label),
          from: asList(changed.from),
          to: text(changed.to),
          by: (text(changed.by) || "handler") as "handler" | "owner",
          role: asList(changed.role),
          needsNote: changed.needs_note === true,
          require: asList(changed.require),
          when: text(changed.when),
          base: false,
          removed: false,
          origin: "added" as Origin,
        };
      }),
  ];
  const stateBase = (name: string): Rec | undefined => {
    const base = baseStates.find((state) => state.name === name);
    return base ? { label: base.label, terminal: base.terminal } : undefined;
  };
  const actionBase = (name: string): Rec | undefined => {
    const base = baseActions.find((action) => action.name === name);
    return base
      ? { label: base.label, from: base.from, to: base.to, by: base.by.toLowerCase(), role: base.roles, needs_note: base.needsNote, require: base.require, when: conditionOf(base.when) }
      : undefined;
  };
  const live = states.filter((state) => !state.removed);
  return { states, actions, live, stateBase, actionBase };
}

// The states or actions of the file after one row changes. A change to a row of the template is written
// as what differs from the template; a row the file adds is written whole; null removes a template row.
export function writeSpec(current: Rec, name: string, spec: Rec | null | undefined, base?: Rec): Rec | undefined {
  const next = { ...current };
  if (spec === undefined) delete next[name];
  else if (spec === null) next[name] = null;
  else {
    const kept: Rec = {};
    Object.entries(spec).forEach(([k, v]) => {
      if (base && JSON.stringify(base[k]) === JSON.stringify(v)) return;
      if (v === undefined || v === "" || (Array.isArray(v) && v.length === 0 && !(base && Array.isArray(base[k]) && (base[k] as unknown[]).length > 0))) return;
      if (!base && v === false) return;
      kept[k] = v;
    });
    if (base && Object.keys(kept).length === 0) delete next[name];
    else next[name] = kept;
  }
  return Object.keys(next).length > 0 ? next : undefined;
}
