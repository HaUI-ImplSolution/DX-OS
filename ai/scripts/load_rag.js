// SPDX-License-Identifier: Apache-2.0
// Run through benchmark_rag.py so each trial also has resource samples and provenance.
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Rate } from 'k6/metrics';

const namespace = __ENV.RAG_LOAD_NAMESPACE;
const vus = Number(__ENV.RAG_LOAD_VUS);
const seconds = Number(__ENV.RAG_LOAD_SECONDS);
if (!['farm', 'lab'].includes(namespace) || ![1, 2, 5].includes(vus)
    || !Number.isInteger(seconds) || seconds < 10 || seconds > 300
    || !__ENV.RAG_LOAD_URL || !__ENV.AI_SERVICE_API_KEY || !__ENV.RAG_LOAD_OUTPUT) {
  throw new Error('Missing or invalid local benchmark configuration');
}
const questions = open('../../data/rag/evaluation/questions.jsonl').trim().split('\n')
  .map(JSON.parse).filter(q => q.group === `${namespace}_qa`);
if (!questions.length) {
  throw new Error('Benchmark question count differs from the accepted pilot');
}
http.setResponseCallback(http.expectedStatuses(200));
const invalid = new Rate('rag_invalid_response');

export const options = {
  scenarios: { retrieval: { executor: 'constant-vus', vus, duration: `${seconds}s`, gracefulStop: '15s' } },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: { http_req_failed: ['rate==0'], rag_invalid_response: ['rate==0'] },
  tags: { namespace, vus: String(vus), boundary: 'ai_http_retrieval' },
};

export default function () {
  const q = questions[exec.scenario.iterationInTest % questions.length];
  const response = http.post(__ENV.RAG_LOAD_URL, JSON.stringify({
    query: q.query, namespace, top_k: 4, document_type: null,
  }), {
    headers: { 'Content-Type': 'application/json', 'X-Service-Api-Key': __ENV.AI_SERVICE_API_KEY },
    timeout: '10s', tags: { qid: q.qid },
  });
  let valid = false;
  if (response.status === 200) {
    try {
      const envelope = response.json();
      const rows = envelope.passages;
      valid = envelope.namespace === namespace && ["ANSWERABLE", "OUT_OF_SCOPE", "IN_SCOPE_NO_EVIDENCE"].includes(envelope.scope_status) && envelope.reason_code !== "RERANK_UNAVAILABLE" && Array.isArray(rows) && rows.length <= 4 && rows.every(row =>
        typeof row.content === 'string' && row.content.length > 0
        && typeof row.document_title === 'string'
        && Number.isInteger(row.chunk_index) && Number.isFinite(row.score)
        && Number.isFinite(row.similarity) && Array.isArray(row.source_ids));
    } catch (_) { /* Count malformed responses; never dump bodies or credentials. */ }
  }
  check(response, { 'HTTP 200 and valid search envelope': () => valid });
  invalid.add(!valid);
}

export function handleSummary(data) {
  return { [`${__ENV.RAG_LOAD_OUTPUT}/summary.json`]: JSON.stringify(data, null, 2) };
}
