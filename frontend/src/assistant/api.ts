// SPDX-License-Identifier: Apache-2.0
import { json, request } from "../shell/api";

export interface Capabilities {
  enabled: boolean;
  generationEnabled: boolean;
  operations: string[];
  maxCandidates: number;
  maxEvents: number;
  topK: number;
}

export interface AssistantQuery {
  operation: "find" | "summarize" | "similar";
  model: string;
  entity: string;
  query?: string;
  filters?: Record<string, string>;
  recordId?: number;
  interpretQuery?: boolean;
  historyWindow?: { from: string | null; to: string | null } | null;
}

export interface AssistantResult {
  status: string;
  reasonCode: string;
  operation: string;
  model: string;
  entity: string;
  filters: Record<string, string>;
  coverage: {
    totalMatching: number;
    candidateCount: number;
    complete: boolean;
    historyWindow: { from: string | null; to: string | null } | null;
    historySearched: boolean;
  };
  records: { id: number; title: string; status: string; score: number }[];
  evidence: { id: string; kind: string; recordId: number; eventId: number | null; title: string; content: string }[];
  summary: { text: string; evidenceIds: string[] }[];
  generationStatus: string;
  readAt: string;
  timingsMs: Record<string, number>;
}

export const capabilities = () => request<Capabilities>("/assistant/capabilities");
export const queryAssistant = (payload: AssistantQuery, signal: AbortSignal) =>
  request<AssistantResult>("/assistant/query", { ...json("POST", payload), signal });
