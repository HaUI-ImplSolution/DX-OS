// SPDX-License-Identifier: Apache-2.0
import { useMemo } from "react";
import { Box, Typography } from "@mui/material";
import { Background, Controls, MarkerType, ReactFlow, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type { Family } from "./api";

const WITHDRAW = "withdraw";

// The states and actions of a process family, laid out from the first state to the final ones.
export default function ProcessDiagram({ family }: { family: Family }) {
  const { nodes, edges } = useMemo(() => layout(family), [family]);
  return (
    <Box sx={{ height: 380, border: 1, borderColor: "divider" }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        fitView
        fitViewOptions={{ padding: 0.15, minZoom: 0.6, maxZoom: 1.1 }}
        nodesDraggable={false} nodesConnectable={false} proOptions={{ hideAttribution: true }}>
        <Background />
        <Controls showInteractive={false} />
      </ReactFlow>
      <Typography variant="caption" sx={{ display: "block", mt: 0.5 }}>
        Người nộp có thể rút lại ở mọi trạng thái chưa kết thúc.
      </Typography>
    </Box>
  );
}

function layout(family: Family): { nodes: Node[]; edges: Edge[] } {
  const steps = family.actions.filter((action) => action.name !== WITHDRAW);
  const depth = new Map<string, number>([[family.initial, 0]]);
  const queue = [family.initial];
  while (queue.length) {
    const current = queue.shift() as string;
    steps
      .filter((action) => action.from.includes(current))
      .forEach((action) => {
        if (!depth.has(action.to)) {
          depth.set(action.to, (depth.get(current) ?? 0) + 1);
          queue.push(action.to);
        }
      });
  }
  const rows = new Map<number, number>();
  const nodes: Node[] = family.states
    .filter((state) => depth.has(state.name))
    .map((state) => {
      const column = depth.get(state.name) ?? 0;
      const row = rows.get(column) ?? 0;
      rows.set(column, row + 1);
      return {
        id: state.name,
        position: { x: column * 320, y: row * 90 },
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
    action.from.map((from) => ({
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
