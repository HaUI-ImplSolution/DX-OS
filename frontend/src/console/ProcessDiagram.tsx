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
import type { Family } from "./api";
import {
  buildProcessDiagram,
  PROCESS_ANCHORS,
  PROCESS_NODE_HEIGHT,
  PROCESS_NODE_WIDTH,
  type ProcessAnchor,
} from "./processDiagramCore";

const BOX_HEIGHT = 360;
const LABEL_OFFSET = 18;

type Anchor = ProcessAnchor & {
  position: Position;
  style: CSSProperties;
};
type ProcessNodeData = { label: string; terminal: boolean };
type ProcessNode = Node<ProcessNodeData, "process">;
type DirectEdgeData = { label: string; labelOffset: number; labelSide: 1 | -1; labelT: number };
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
  const { nodes, edges } = useMemo(() => adaptProcessDiagram(family, spacing), [family, spacing]);
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
      </SmartEdgeProvider>
      <Typography variant="caption" sx={{ display: "block", mt: 0.5 }}>
        Người nộp có thể rút lại ở mọi trạng thái chưa kết thúc.
      </Typography>
    </Box>
  );
}

function adaptProcessDiagram(family: Family, spacing: number): { nodes: ProcessNode[]; edges: DirectEdge[] } {
  const diagram = buildProcessDiagram(family, spacing);
  const nodes: ProcessNode[] = diagram.nodes.map((node) => ({
    id: node.id,
    type: "process",
    position: node.position,
    measured: { width: PROCESS_NODE_WIDTH, height: PROCESS_NODE_HEIGHT },
    data: { label: node.label, terminal: node.terminal },
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
    },
    markerEnd: { type: MarkerType.ArrowClosed },
    style: { strokeWidth: 1.5 },
  }));
  return { nodes, edges };
}
