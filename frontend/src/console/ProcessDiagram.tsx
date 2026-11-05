// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useRef, useState } from "react";
import { Graph, layout as layoutGraph } from "@dagrejs/dagre";
import { Box, Typography } from "@mui/material";
import { Background, Controls, MarkerType, ReactFlow, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type { Family } from "./api";

const WITHDRAW = "withdraw";
const NODE_WIDTH = 150;
const NODE_HEIGHT = 40;
const BOX_HEIGHT = 360;

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
        defaultViewport={viewport}
        minZoom={0.3}
        nodesDraggable={false} nodesConnectable={false} proOptions={{ hideAttribution: true }}
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

function layout(family: Family, spacing: number): { nodes: Node[]; edges: Edge[] } {
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
  const nodes: Node[] = states.map((state) => {
    const point = graph.node(state.name);
    return {
      id: state.name,
      position: { x: point.x - NODE_WIDTH / 2, y: point.y - NODE_HEIGHT / 2 },
      data: { label: state.label },
      style: {
        width: 150,
        background: "#ffffff",
        color: "#111111",
        fontSize: 13,
        borderRadius: state.terminal ? 24 : 6,
        border: state.terminal ? "2px solid #2e7d32" : "1px solid #1976d2",
      },
    };
  });
  const edges: Edge[] = steps.flatMap((action) =>
    action.from.filter((from) => visible.has(from) && visible.has(action.to)).map((from) => ({
      id: `${action.name}-${from}`,
      source: from,
      target: action.to,
      label: `${action.label} (${action.by === "OWNER" ? "người nộp" : "người xử lý"})`,
      markerEnd: { type: MarkerType.ArrowClosed },
      labelStyle: { fontSize: 12, fill: "#111111" },
      labelBgStyle: { fill: "#ffffff" },
      style: { strokeWidth: 1.5 },
    })),
  );
  return { nodes, edges };
}
