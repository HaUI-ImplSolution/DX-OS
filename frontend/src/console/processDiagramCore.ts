// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { Graph, layout as layoutGraph } from "@dagrejs/dagre";
import { formatDuration } from "../shell/format";
import type { Completion, ProcessView } from "./api";
import { t, ta } from "../shell/t";

const WITHDRAW = "withdraw";
export const PROCESS_NODE_WIDTH = 150;
export const PROCESS_NODE_HEIGHT = 40;
const LABEL_GAP = 8;

type Point = { x: number; y: number };
type Rect = Point & { id: string; width: number; height: number };

export type ProcessAnchor = {
  id: string;
  side: "top" | "right" | "bottom" | "left";
  xRatio: number;
  yRatio: number;
  normal: Point;
};

export type ProcessDiagramNode = {
  id: string;
  position: Point;
  label: string;
  terminal: boolean;
  // A second line under the label: the deadline of a waiting state or the completions of a final one.
  caption: string;
};

export type ProcessDiagramEdge = {
  id: string;
  source: string;
  target: string;
  sourceAnchorId: string;
  targetAnchorId: string;
  label: string;
  labelOffset: number;
  labelSide: 1 | -1;
  labelT: number;
  dashed: boolean;
};

type EdgeSpec = { id: string; source: string; target: string; label: string; dashed?: boolean };
type RoutedEdge = EdgeSpec & {
  sourceAnchor: ProcessAnchor;
  targetAnchor: ProcessAnchor;
  sourcePoint: Point;
  targetPoint: Point;
  labelOffset: number;
  labelSide: 1 | -1;
  labelT: number;
};

// Two points on each side let parallel actions leave a state without sharing the same line.
export const PROCESS_ANCHORS: ProcessAnchor[] = [
  { id: "top-left", side: "top", xRatio: 1 / 3, yRatio: 0, normal: { x: 0, y: -1 } },
  { id: "top-right", side: "top", xRatio: 2 / 3, yRatio: 0, normal: { x: 0, y: -1 } },
  { id: "right-top", side: "right", xRatio: 1, yRatio: 1 / 3, normal: { x: 1, y: 0 } },
  { id: "right-bottom", side: "right", xRatio: 1, yRatio: 2 / 3, normal: { x: 1, y: 0 } },
  { id: "bottom-right", side: "bottom", xRatio: 2 / 3, yRatio: 1, normal: { x: 0, y: 1 } },
  { id: "bottom-left", side: "bottom", xRatio: 1 / 3, yRatio: 1, normal: { x: 0, y: 1 } },
  { id: "left-bottom", side: "left", xRatio: 0, yRatio: 2 / 3, normal: { x: -1, y: 0 } },
  { id: "left-top", side: "left", xRatio: 0, yRatio: 1 / 3, normal: { x: -1, y: 0 } },
];

// The wording of what a final state does for a record that reaches it.
function completionText(completion: Completion): string {
  const group = completion.group ?? `{${completion.groupField}}`;
  return completion.operation === "remove_from_group" ? t("gỡ khỏi nhóm {group}", { group }) : t("thêm vào nhóm {group}", { group });
}

export function buildProcessDiagram(
  family: ProcessView,
  spacing: number,
): { nodes: ProcessDiagramNode[]; edges: ProcessDiagramEdge[] } {
  const steps = family.actions.filter((action) => action.name !== WITHDRAW);
  const reachable = new Set<string>([family.initial]);
  const queue = [family.initial];
  while (queue.length) {
    const current = queue.shift() as string;
    steps
      .filter((action) => action.from.includes(current))
      .forEach((action) => {
        if (!reachable.has(action.to)) {
          reachable.add(action.to);
          queue.push(action.to);
        }
      });
  }
  const states = family.states.filter((state) => reachable.has(state.name));
  const visible = new Set(states.map((state) => state.name));
  const graph = new Graph();
  graph.setGraph({
    rankdir: "LR",
    ranksep: Math.max(0, spacing - PROCESS_NODE_WIDTH),
    nodesep: 50,
  });
  graph.setDefaultEdgeLabel(() => ({}));
  states.forEach((state) =>
    graph.setNode(state.name, { width: PROCESS_NODE_WIDTH, height: PROCESS_NODE_HEIGHT }),
  );
  steps.forEach((action) =>
    action.from
      .filter((from) => visible.has(from) && visible.has(action.to))
      .forEach((from) => graph.setEdge(from, action.to)),
  );
  layoutGraph(graph, { useDynamic: false });

  const nodes: ProcessDiagramNode[] = states.map((state) => {
    const point = graph.node(state.name);
    return {
      id: state.name,
      position: {
        x: point.x - PROCESS_NODE_WIDTH / 2,
        y: point.y - PROCESS_NODE_HEIGHT / 2,
      },
      label: t(state.label),
      terminal: state.terminal,
      caption: captionOf(family, state),
    };
  });
  const specs: EdgeSpec[] = steps.flatMap((action) =>
    action.from.filter((from) => visible.has(from) && visible.has(action.to)).map((from) => ({
      id: `${action.name}-${from}`,
      source: from,
      target: action.to,
      label: `${ta(action.label)} (${action.by === "OWNER" ? t("người nộp") : (action.roles.length ? action.roles : family.handlers).join(", ")})${action.when ? t(", khi {field} {op} {value}", { field: action.when.field, op: action.when.op, value: action.when.value }) : ""}`,
    })),
  );
  // The system takes the action of auto_close for the owner of a record that waits that long.
  const closing = family.autoCloseSeconds === null ? undefined : family.actions.find((action) => action.name === (family.autoCloseAction ?? "confirm"));
  const closeFrom = closing?.from.find((from) => visible.has(from));
  if (closing && closeFrom && visible.has(closing.to)) {
    specs.push({
      id: "auto_close",
      source: closeFrom,
      target: closing.to,
      label: t("tự đóng sau {time}", { time: formatDuration(family.autoCloseSeconds ?? 0) }),
      dashed: true,
    });
  }
  const rects = new Map(
    nodes.map((node) => [
      node.id,
      {
        id: node.id,
        x: node.position.x,
        y: node.position.y,
        width: PROCESS_NODE_WIDTH,
        height: PROCESS_NODE_HEIGHT,
      },
    ]),
  );
  const edges = routeEdges(specs, rects).map((route) => ({
    id: route.id,
    source: route.source,
    target: route.target,
    sourceAnchorId: route.sourceAnchor.id,
    targetAnchorId: route.targetAnchor.id,
    label: route.label,
    labelOffset: route.labelOffset,
    labelSide: route.labelSide,
    labelT: route.labelT,
    dashed: route.dashed ?? false,
  }));
  return { nodes, edges };
}

function captionOf(family: ProcessView, state: ProcessView["states"][number]): string {
  if (state.completions.length) return state.completions.map(completionText).join("; ");
  if (family.deadlineSeconds !== null && family.awaiting.includes(state.name)) {
    return t("hạn {time}", { time: formatDuration(family.deadlineSeconds) });
  }
  return "";
}

function routeEdges(specs: EdgeSpec[], rects: Map<string, Rect>): RoutedEdge[] {
  const routed: RoutedEdge[] = [];
  const anchorUsage = new Map<string, number>();
  const labelRects: Rect[] = [];
  for (const spec of specs) {
    const source = rects.get(spec.source);
    const target = rects.get(spec.target);
    if (!source || !target) continue;
    let best: Omit<RoutedEdge, keyof EdgeSpec | "labelOffset" | "labelSide" | "labelT"> | undefined;
    let bestScore = Number.POSITIVE_INFINITY;
    for (const sourceAnchor of PROCESS_ANCHORS) {
      const sourcePoint = anchorPoint(source, sourceAnchor);
      for (const targetAnchor of PROCESS_ANCHORS) {
        const targetPoint = anchorPoint(target, targetAnchor);
        const dx = targetPoint.x - sourcePoint.x;
        const dy = targetPoint.y - sourcePoint.y;
        const length = Math.hypot(dx, dy);
        if (length < 1) continue;
        const direction = { x: dx / length, y: dy / length };
        if (dot(direction, sourceAnchor.normal) <= 0.05 || dot(direction, targetAnchor.normal) >= -0.05) continue;
        let score = length;
        for (const rect of rects.values()) {
          if (
            rect.id !== source.id
            && rect.id !== target.id
            && segmentCrossesRect(sourcePoint, targetPoint, expandRect(rect, 8))
          ) {
            score += 100_000;
          }
        }
        score += (anchorUsage.get(`${source.id}:${sourceAnchor.id}`) ?? 0) * 260;
        score += (anchorUsage.get(`${target.id}:${targetAnchor.id}`) ?? 0) * 260;
        for (const previous of routed) {
          if (segmentsIntersect(sourcePoint, targetPoint, previous.sourcePoint, previous.targetPoint)) score += 5_000;
          if (sameNodePair(spec, previous)) {
            score += 400;
            if (
              distance(sourcePoint, previous.targetPoint) < 10
              || distance(targetPoint, previous.sourcePoint) < 10
            ) {
              score += 4_000;
            }
          }
        }
        if (score < bestScore) {
          bestScore = score;
          best = { sourceAnchor, targetAnchor, sourcePoint, targetPoint };
        }
      }
    }
    if (!best) continue;
    increment(anchorUsage, `${source.id}:${best.sourceAnchor.id}`);
    increment(anchorUsage, `${target.id}:${best.targetAnchor.id}`);
    const label = placeLabel(spec.label, best.sourcePoint, best.targetPoint, rects, labelRects, routed);
    labelRects.push(label.rect);
    routed.push({ ...spec, ...best, labelOffset: label.offset, labelSide: label.side, labelT: label.t });
  }
  return routed;
}

function placeLabel(
  label: string,
  source: Point,
  target: Point,
  nodeRects: Map<string, Rect>,
  placed: Rect[],
  routes: RoutedEdge[],
): { offset: number; rect: Rect; side: 1 | -1; t: number } {
  const width = estimateLabelWidth(label);
  const height = 22;
  const dx = target.x - source.x;
  const dy = target.y - source.y;
  const length = Math.max(1, Math.hypot(dx, dy));
  const normal = { x: -dy / length, y: dx / length };
  const clearance = Math.abs(normal.x) * width / 2 + Math.abs(normal.y) * height / 2 + LABEL_GAP;
  let best: { offset: number; rect: Rect; side: 1 | -1; t: number } | undefined;
  let bestScore = Number.POSITIVE_INFINITY;
  for (const offset of [clearance, clearance + 16, clearance + 32, clearance + 48]) {
    for (const t of [0.5, 0.38, 0.62, 0.26, 0.74, 0.14, 0.86]) {
      for (const side of [1, -1] as const) {
        const center = {
          x: source.x + dx * t + normal.x * offset * side,
          y: source.y + dy * t + normal.y * offset * side,
        };
        const rect = { id: "label", x: center.x - width / 2, y: center.y - height / 2, width, height };
        let score = Math.abs(t - 0.5) * 100 + (offset - clearance) * 2 + (side === -1 ? 1 : 0);
        if (segmentCrossesRect(source, target, expandRect(rect, 2))) score += 200_000;
        for (const node of nodeRects.values()) {
          const overlap = rectOverlapArea(rect, expandRect(node, 4));
          if (overlap > 0) score += 100_000 + overlap * 100;
        }
        for (const other of placed) {
          const overlap = rectOverlapArea(rect, expandRect(other, 8));
          if (overlap > 0) score += 100_000 + overlap * 100;
        }
        for (const route of routes) {
          if (segmentCrossesRect(route.sourcePoint, route.targetPoint, expandRect(rect, 3))) score += 20_000;
        }
        if (score < bestScore) {
          bestScore = score;
          best = { offset, rect, side, t };
        }
      }
    }
  }
  return best as { offset: number; rect: Rect; side: 1 | -1; t: number };
}

function estimateLabelWidth(label: string): number {
  const textWidth = [...label].reduce((width, character) => {
    if (character === " ") return width + 3.5;
    if ("()[]{}.,:;!|'".includes(character)) return width + 4;
    if ("mwMW@%".includes(character)) return width + 9.5;
    if (character === character.toUpperCase() && character !== character.toLowerCase()) return width + 7.5;
    return width + 6.4;
  }, 0);
  return Math.max(72, textWidth + 12);
}

function anchorPoint(rect: Rect, anchor: ProcessAnchor): Point {
  return { x: rect.x + rect.width * anchor.xRatio, y: rect.y + rect.height * anchor.yRatio };
}

function expandRect(rect: Rect, amount: number): Rect {
  return {
    ...rect,
    x: rect.x - amount,
    y: rect.y - amount,
    width: rect.width + amount * 2,
    height: rect.height + amount * 2,
  };
}

function rectOverlapArea(a: Rect, b: Rect): number {
  const width = Math.max(0, Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x));
  const height = Math.max(0, Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y));
  return width * height;
}

function segmentCrossesRect(a: Point, b: Point, rect: Rect): boolean {
  if (pointInRect(a, rect) || pointInRect(b, rect)) return true;
  const topLeft = { x: rect.x, y: rect.y };
  const topRight = { x: rect.x + rect.width, y: rect.y };
  const bottomRight = { x: rect.x + rect.width, y: rect.y + rect.height };
  const bottomLeft = { x: rect.x, y: rect.y + rect.height };
  return segmentsIntersect(a, b, topLeft, topRight)
    || segmentsIntersect(a, b, topRight, bottomRight)
    || segmentsIntersect(a, b, bottomRight, bottomLeft)
    || segmentsIntersect(a, b, bottomLeft, topLeft);
}

function pointInRect(point: Point, rect: Rect): boolean {
  return point.x >= rect.x && point.x <= rect.x + rect.width
    && point.y >= rect.y && point.y <= rect.y + rect.height;
}

function segmentsIntersect(a: Point, b: Point, c: Point, d: Point): boolean {
  const abC = cross(a, b, c);
  const abD = cross(a, b, d);
  const cdA = cross(c, d, a);
  const cdB = cross(c, d, b);
  if (((abC > 0 && abD < 0) || (abC < 0 && abD > 0))
      && ((cdA > 0 && cdB < 0) || (cdA < 0 && cdB > 0))) return true;
  const epsilon = 0.001;
  return (Math.abs(abC) < epsilon && onSegment(a, b, c))
    || (Math.abs(abD) < epsilon && onSegment(a, b, d))
    || (Math.abs(cdA) < epsilon && onSegment(c, d, a))
    || (Math.abs(cdB) < epsilon && onSegment(c, d, b));
}

function cross(a: Point, b: Point, point: Point): number {
  return (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x);
}

function onSegment(a: Point, b: Point, point: Point): boolean {
  return point.x >= Math.min(a.x, b.x) && point.x <= Math.max(a.x, b.x)
    && point.y >= Math.min(a.y, b.y) && point.y <= Math.max(a.y, b.y);
}

function dot(a: Point, b: Point): number {
  return a.x * b.x + a.y * b.y;
}

function distance(a: Point, b: Point): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function sameNodePair(a: Pick<EdgeSpec, "source" | "target">, b: Pick<EdgeSpec, "source" | "target">): boolean {
  return a.source === b.target && a.target === b.source;
}

function increment(values: Map<string, number>, key: string): void {
  values.set(key, (values.get(key) ?? 0) + 1);
}
