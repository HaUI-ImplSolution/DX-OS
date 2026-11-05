// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { Graph, layout as layoutGraph } from "@dagrejs/dagre";
import { Box, Typography } from "@mui/material";
import {
  Background,
  BaseEdge,
  Controls,
  EdgeLabelRenderer,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  type Edge,
  type EdgeProps,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type { Family } from "./api";

const WITHDRAW = "withdraw";
const NODE_WIDTH = 150;
const NODE_HEIGHT = 40;
const BOX_HEIGHT = 360;
const LABEL_OFFSET = 18;
const LABEL_GAP = 8;

type Point = { x: number; y: number };
type Rect = Point & { id: string; width: number; height: number };
type Anchor = {
  id: string;
  position: Position;
  xRatio: number;
  yRatio: number;
  normal: Point;
  style: CSSProperties;
};
type ProcessNodeData = { label: string; terminal: boolean };
type ProcessNode = Node<ProcessNodeData, "process">;
type DirectEdgeData = { label: string; labelOffset: number; labelSide: 1 | -1; labelT: number };
type DirectEdge = Edge<DirectEdgeData, "direct">;
type EdgeSpec = { id: string; source: string; target: string; label: string };
type RoutedEdge = EdgeSpec & {
  sourceAnchor: Anchor;
  targetAnchor: Anchor;
  sourcePoint: Point;
  targetPoint: Point;
  labelOffset: number;
  labelSide: 1 | -1;
  labelT: number;
};

// Two points on each side let parallel actions leave a state without sharing the same line.
const ANCHORS: Anchor[] = [
  { id: "top-left", position: Position.Top, xRatio: 1 / 3, yRatio: 0, normal: { x: 0, y: -1 }, style: { left: "33.333%" } },
  { id: "top-right", position: Position.Top, xRatio: 2 / 3, yRatio: 0, normal: { x: 0, y: -1 }, style: { left: "66.667%" } },
  { id: "right-top", position: Position.Right, xRatio: 1, yRatio: 1 / 3, normal: { x: 1, y: 0 }, style: { top: "33.333%" } },
  { id: "right-bottom", position: Position.Right, xRatio: 1, yRatio: 2 / 3, normal: { x: 1, y: 0 }, style: { top: "66.667%" } },
  { id: "bottom-right", position: Position.Bottom, xRatio: 2 / 3, yRatio: 1, normal: { x: 0, y: 1 }, style: { left: "66.667%" } },
  { id: "bottom-left", position: Position.Bottom, xRatio: 1 / 3, yRatio: 1, normal: { x: 0, y: 1 }, style: { left: "33.333%" } },
  { id: "left-bottom", position: Position.Left, xRatio: 0, yRatio: 2 / 3, normal: { x: -1, y: 0 }, style: { top: "66.667%" } },
  { id: "left-top", position: Position.Left, xRatio: 0, yRatio: 1 / 3, normal: { x: -1, y: 0 }, style: { top: "33.333%" } },
];

const HANDLE_STYLE: CSSProperties = {
  width: 4,
  height: 4,
  minWidth: 0,
  minHeight: 0,
  border: 0,
  background: "transparent",
  opacity: 0,
};

function ProcessStateNode({ data }: NodeProps<ProcessNode>) {
  return (
    <Box
      sx={{
        width: "100%",
        height: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        boxSizing: "border-box",
        px: 1,
        background: "#ffffff",
        color: "#111111",
        fontSize: 13,
        borderRadius: data.terminal ? 6 : 1,
        border: data.terminal ? "2px solid #2e7d32" : "1px solid #1976d2",
      }}>
      {data.label}
      {ANCHORS.flatMap((anchor) => [
        <Handle
          key={`target-${anchor.id}`}
          id={`target-${anchor.id}`}
          type="target"
          position={anchor.position}
          style={{ ...HANDLE_STYLE, ...anchor.style }}
        />,
        <Handle
          key={`source-${anchor.id}`}
          id={`source-${anchor.id}`}
          type="source"
          position={anchor.position}
          style={{ ...HANDLE_STYLE, ...anchor.style }}
        />,
      ])}
    </Box>
  );
}

function DirectEdge({ id, sourceX, sourceY, targetX, targetY, data, markerEnd, style }: EdgeProps<DirectEdge>) {
  const dx = targetX - sourceX;
  const dy = targetY - sourceY;
  const length = Math.max(1, Math.hypot(dx, dy));
  const labelT = data?.labelT ?? 0.5;
  const offset = (data?.labelOffset ?? LABEL_OFFSET) * (data?.labelSide ?? 1);
  const labelX = sourceX + dx * labelT + (-dy / length) * offset;
  const labelY = sourceY + dy * labelT + (dx / length) * offset;
  return (
    <>
      <BaseEdge
        id={id}
        path={`M ${sourceX} ${sourceY} L ${targetX} ${targetY}`}
        markerEnd={markerEnd}
        style={style}
      />
      <EdgeLabelRenderer>
        <Box
          component="span"
          className="nodrag nopan"
          sx={{
            position: "absolute",
            transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
            px: 0.6,
            py: 0.15,
            backgroundColor: "transparent",
            color: "#111111",
            fontSize: 12,
            lineHeight: 1.35,
            whiteSpace: "nowrap",
            pointerEvents: "none",
          }}>
          {data?.label}
        </Box>
      </EdgeLabelRenderer>
    </>
  );
}

const NODE_TYPES = { process: ProcessStateNode };
const EDGE_TYPES = { direct: DirectEdge };

// The states and actions of a process family, laid out from the first state to the final ones.
export default function ProcessDiagram({
  family,
  height = BOX_HEIGHT,
  spacing = 260,
}: {
  family: Family;
  height?: number | string;
  // Distance between the columns of states; a larger box gives the labels of the arrows room.
  spacing?: number;
}) {
  const { nodes, edges } = useMemo(() => layout(family, spacing), [family, spacing]);
  const box = useRef<HTMLDivElement | null>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });
  // The diagram is fitted again whenever its box changes width, so it never keeps the size of a first, narrower layout.
  useEffect(() => {
    if (!box.current) return undefined;
    const observer = new ResizeObserver(([entry]) =>
      setSize({ width: Math.round(entry.contentRect.width), height: Math.round(entry.contentRect.height) }),
    );
    observer.observe(box.current);
    return () => observer.disconnect();
  }, []);
  // Centred and as large as the box allows, up to its natural size.
  const viewport = useMemo(() => {
    const maxX = Math.max(...nodes.map((node) => node.position.x)) + NODE_WIDTH;
    const maxY = Math.max(...nodes.map((node) => node.position.y)) + NODE_HEIGHT;
    const zoom = Math.min(1.1, Math.max(0.3, (size.width - 48) / maxX), Math.max(0.3, (size.height - 48) / maxY));
    return { zoom, x: (size.width - maxX * zoom) / 2, y: (size.height - maxY * zoom) / 2 };
  }, [nodes, size]);
  return (
    <Box ref={box} sx={{ height, border: 1, borderColor: "divider", borderRadius: 1, overflow: "hidden" }}>
      <ReactFlow
        key={`${family.name}-${size.width}-${size.height}`}
        nodes={nodes}
        edges={edges}
        nodeTypes={NODE_TYPES}
        edgeTypes={EDGE_TYPES}
        defaultViewport={viewport}
        minZoom={0.3}
        nodesDraggable={false}
        nodesConnectable={false}
        proOptions={{ hideAttribution: true }}
        // The diagram only shows; its key bindings listen on the whole page and would take Space and
        // Backspace from the model editor beside it.
        panActivationKeyCode={null}
        deleteKeyCode={null}
        selectionKeyCode={null}
        multiSelectionKeyCode={null}
        zoomActivationKeyCode={null}
        disableKeyboardA11y>
        <Background />
        <Controls showInteractive={false} />
      </ReactFlow>
      <Typography variant="caption" sx={{ display: "block", mt: 0.5 }}>
        Người nộp có thể rút lại ở mọi trạng thái chưa kết thúc.
      </Typography>
    </Box>
  );
}

function layout(family: Family, spacing: number): { nodes: ProcessNode[]; edges: DirectEdge[] } {
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
  graph.setGraph({ rankdir: "LR", ranksep: Math.max(0, spacing - NODE_WIDTH), nodesep: 50 });
  graph.setDefaultEdgeLabel(() => ({}));
  states.forEach((state) => graph.setNode(state.name, { width: NODE_WIDTH, height: NODE_HEIGHT }));
  steps.forEach((action) =>
    action.from
      .filter((from) => visible.has(from) && visible.has(action.to))
      .forEach((from) => graph.setEdge(from, action.to)),
  );
  layoutGraph(graph, { useDynamic: false });
  const nodes: ProcessNode[] = states.map((state) => {
    const point = graph.node(state.name);
    return {
      id: state.name,
      type: "process",
      position: { x: point.x - NODE_WIDTH / 2, y: point.y - NODE_HEIGHT / 2 },
      data: { label: state.label, terminal: state.terminal },
      style: { width: NODE_WIDTH, height: NODE_HEIGHT },
    };
  });
  const specs: EdgeSpec[] = steps.flatMap((action) =>
    action.from.filter((from) => visible.has(from) && visible.has(action.to)).map((from) => ({
      id: `${action.name}-${from}`,
      source: from,
      target: action.to,
      label: `${action.label} (${action.by === "OWNER" ? "người nộp" : "người xử lý"})`,
    })),
  );
  const rects = new Map(
    nodes.map((node) => [
      node.id,
      { id: node.id, x: node.position.x, y: node.position.y, width: NODE_WIDTH, height: NODE_HEIGHT },
    ]),
  );
  const edges: DirectEdge[] = routeEdges(specs, rects).map((route) => ({
    id: route.id,
    type: "direct",
    source: route.source,
    target: route.target,
    sourceHandle: `source-${route.sourceAnchor.id}`,
    targetHandle: `target-${route.targetAnchor.id}`,
    data: {
      label: route.label,
      labelOffset: route.labelOffset,
      labelSide: route.labelSide,
      labelT: route.labelT,
    },
    markerEnd: { type: MarkerType.ArrowClosed },
    style: { strokeWidth: 1.5 },
  }));
  return { nodes, edges };
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
    for (const sourceAnchor of ANCHORS) {
      const sourcePoint = anchorPoint(source, sourceAnchor);
      for (const targetAnchor of ANCHORS) {
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

function anchorPoint(rect: Rect, anchor: Anchor): Point {
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
