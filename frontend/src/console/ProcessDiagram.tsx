// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { Box, Typography } from "@mui/material";
import { SmartEdgeProvider, useSmartEdgePath } from "@tisoap/react-flow-smart-edge";
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
import type { ProcessView } from "./api";
import {
  buildProcessDiagram,
  PROCESS_ANCHORS,
  PROCESS_NODE_HEIGHT,
  PROCESS_NODE_WIDTH,
  type ProcessAnchor,
} from "./processDiagramCore";
import { formatDuration } from "../shell/format";
import { t } from "../shell/t";

const BOX_HEIGHT = 360;
const LABEL_OFFSET = 18;
// The arrow the system takes by itself and its label share a colour, so the label is not read as the arrow beside it.
const AUTO_COLOR = "#c25e00";
const SELECTED_COLOR = "#9c27b0";

type Anchor = ProcessAnchor & {
  position: Position;
  style: CSSProperties;
};
type ProcessNodeData = { label: string; terminal: boolean; initial: boolean; selected: boolean; clickable: boolean; caption: string };
type ProcessNode = Node<ProcessNodeData, "process">;
type DirectEdgeData = {
  label: string;
  labelOffset: number;
  labelSide: 1 | -1;
  labelT: number;
  dashed: boolean;
  action: string;
  selected: boolean;
};
type DirectEdge = Edge<DirectEdgeData, "direct">;

const POSITION_BY_SIDE: Record<ProcessAnchor["side"], Position> = {
  top: Position.Top,
  right: Position.Right,
  bottom: Position.Bottom,
  left: Position.Left,
};

const ANCHORS: Anchor[] = PROCESS_ANCHORS.map((anchor) => ({
  ...anchor,
  position: POSITION_BY_SIDE[anchor.side],
  style: anchor.side === "top" || anchor.side === "bottom"
    ? { left: `${Math.round(anchor.xRatio * 100_000) / 1_000}%` }
    : { top: `${Math.round(anchor.yRatio * 100_000) / 1_000}%` },
}));

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
        position: "relative",
        cursor: data.clickable ? "pointer" : "default",
        background: "#ffffff",
        color: "#111111",
        fontSize: 13,
        borderRadius: data.terminal ? 6 : 1,
        border: data.terminal ? "2px solid #2e7d32" : "1px solid #1976d2",
        boxShadow: data.selected ? `0 0 0 3px ${SELECTED_COLOR}` : "none",
      }}>
      {data.initial && (
        <Box
          component="span"
          sx={{ position: "absolute", top: -9, left: 6, px: 0.5, fontSize: 10, lineHeight: "16px", borderRadius: 0.5, background: "#1976d2", color: "#ffffff" }}>
          {t("Bắt đầu")}
        </Box>
      )}
      <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center", minWidth: 0, lineHeight: 1.25 }}>
        {data.label}
        {data.caption && (
          <Box
            component="span"
            title={data.caption}
            sx={{ maxWidth: "100%", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", fontSize: 10, color: "#555555" }}>
            {data.caption}
          </Box>
        )}
      </Box>
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

function DirectEdge({
  id,
  source,
  target,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
  markerEnd,
  style,
}: EdgeProps<DirectEdge>) {
  const { route } = useSmartEdgePath({
    id,
    source,
    target,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    preset: "straight",
  });
  const dx = targetX - sourceX;
  const dy = targetY - sourceY;
  const length = Math.max(1, Math.hypot(dx, dy));
  const labelT = data?.labelT ?? 0.5;
  const offset = (data?.labelOffset ?? LABEL_OFFSET) * (data?.labelSide ?? 1);
  const labelCenterX = route?.kind === "routed" ? route.edgeCenterX : sourceX + dx * labelT;
  const labelCenterY = route?.kind === "routed" ? route.edgeCenterY : sourceY + dy * labelT;
  const labelX = labelCenterX + (-dy / length) * offset;
  const labelY = labelCenterY + (dx / length) * offset;
  return (
    <>
      <BaseEdge
        id={id}
        path={
          route?.kind === "routed"
            ? route.svgPathString
            : `M ${sourceX} ${sourceY} L ${targetX} ${targetY}`
        }
        markerEnd={markerEnd}
        style={{
          ...style,
          ...(data?.dashed ? { strokeDasharray: "6 4", stroke: AUTO_COLOR } : {}),
          ...(data?.selected ? { stroke: SELECTED_COLOR, strokeWidth: 3 } : {}),
        }}
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
            color: data?.selected ? SELECTED_COLOR : data?.dashed ? AUTO_COLOR : "#111111",
            fontStyle: data?.dashed ? "italic" : "normal",
            fontWeight: data?.selected ? 600 : 400,
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

// The states and actions of the process an entity declares, laid out from the first state to the final ones.
// Given the select callbacks, a state or an arrow opens where it is edited.
export default function ProcessDiagram({
  family,
  height = BOX_HEIGHT,
  spacing = 260,
  selectedState,
  selectedAction,
  onSelectState,
  onSelectAction,
}: {
  family: ProcessView;
  height?: number | string;
  // Distance between the columns of states; a larger box gives the labels of the arrows room.
  spacing?: number;
  selectedState?: string;
  selectedAction?: string;
  onSelectState?: (state: string) => void;
  onSelectAction?: (action: string) => void;
}) {
  const clickable = onSelectState !== undefined || onSelectAction !== undefined;
  const { nodes, edges } = useMemo(
    () => adaptProcessDiagram(family, spacing, { state: selectedState, action: selectedAction, clickable }),
    [family, spacing, selectedState, selectedAction, clickable],
  );
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
    const maxX = Math.max(...nodes.map((node) => node.position.x)) + PROCESS_NODE_WIDTH;
    const maxY = Math.max(...nodes.map((node) => node.position.y)) + PROCESS_NODE_HEIGHT;
    const zoom = Math.min(1.1, Math.max(0.3, (size.width - 48) / maxX), Math.max(0.3, (size.height - 48) / maxY));
    return { zoom, x: (size.width - maxX * zoom) / 2, y: (size.height - maxY * zoom) / 2 };
  }, [nodes, size]);
  return (
    <Box ref={box} sx={{ height, border: 1, borderColor: "divider", borderRadius: 1, overflow: "hidden" }}>
      <SmartEdgeProvider nodes={nodes}>
        <ReactFlow
          key={`${family.entity}-${family.family}-${size.width}-${size.height}`}
          nodes={nodes}
          edges={edges}
          nodeTypes={NODE_TYPES}
          edgeTypes={EDGE_TYPES}
          defaultViewport={viewport}
          minZoom={0.3}
          nodesDraggable={false}
          nodesConnectable={false}
          onNodeClick={(_, node) => onSelectState?.(node.id)}
          onEdgeClick={(_, edge) => edge.data && onSelectAction?.(edge.data.action)}
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
      </SmartEdgeProvider>
      <Typography variant="caption" sx={{ display: "block", mt: 0.5 }}>
        {notes(family)}
        {clickable ? ` ${t("Bấm vào một trạng thái hoặc mũi tên để sửa.")}` : ""}
      </Typography>
    </Box>
  );
}

// What the file says about the process that the boxes and arrows do not show.
function notes(process: ProcessView): string {
  const handlers = process.handlers.join(", ");
  const lines = [
    process.allowSelfDecision
      ? t("Người xử lý: {handlers}, người nộp cũng được xử lý hồ sơ của mình.", { handlers })
      : t("Người xử lý: {handlers}.", { handlers }),
  ];
  if (process.booking) {
    const { resource, starts, ends, capacity } = process.booking;
    lines.push(t("Đặt chỗ: tài nguyên {resource}, từ {starts} đến {ends}, sức chứa {capacity}.", { resource, starts, ends, capacity }));
  }
  for (const level of process.escalations) {
    const roles = level.to.join(", ");
    const after = formatDuration(level.afterSeconds);
    lines.push(
      level.everySeconds === null
        ? t("Quá hạn {after} thì báo {roles}.", { after, roles })
        : t("Quá hạn {after} thì báo {roles}, nhắc {times} lần, cách nhau {every}.", { after, roles, times: level.times, every: formatDuration(level.everySeconds) }),
    );
  }
  const withdraw = process.actions.find((action) => action.name === "withdraw");
  if (withdraw) {
    const labels = withdraw.from.map((name) => process.states.find((state) => state.name === name)?.label ?? name);
    lines.push(t("Người nộp có thể rút lại hồ sơ ở: {labels}.", { labels: labels.join(", ") }));
  } else {
    lines.push(t("Người nộp không rút lại được hồ sơ."));
  }
  return lines.join(" ");
}

function adaptProcessDiagram(
  family: ProcessView,
  spacing: number,
  selection: { state?: string; action?: string; clickable: boolean },
): { nodes: ProcessNode[]; edges: DirectEdge[] } {
  const diagram = buildProcessDiagram(family, spacing);
  const nodes: ProcessNode[] = diagram.nodes.map((node) => ({
    id: node.id,
    type: "process",
    position: node.position,
    measured: { width: PROCESS_NODE_WIDTH, height: PROCESS_NODE_HEIGHT },
    data: {
      label: node.label,
      terminal: node.terminal,
      initial: node.initial,
      selected: node.id === selection.state,
      clickable: selection.clickable,
      caption: node.caption,
    },
    style: { width: PROCESS_NODE_WIDTH, height: PROCESS_NODE_HEIGHT },
  }));
  const edges: DirectEdge[] = diagram.edges.map((edge) => ({
    id: edge.id,
    type: "direct",
    source: edge.source,
    target: edge.target,
    sourceHandle: `source-${edge.sourceAnchorId}`,
    targetHandle: `target-${edge.targetAnchorId}`,
    data: {
      label: edge.label,
      labelOffset: edge.labelOffset,
      labelSide: edge.labelSide,
      labelT: edge.labelT,
      dashed: edge.dashed,
      action: edge.action,
      selected: edge.action === selection.action,
    },
    markerEnd: edge.dashed ? { type: MarkerType.ArrowClosed, color: AUTO_COLOR } : { type: MarkerType.ArrowClosed },
    style: { strokeWidth: 1.5 },
  }));
  return { nodes, edges };
}
