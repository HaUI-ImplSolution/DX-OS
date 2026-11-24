// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { createHash, randomBytes } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { ApiError } from './errors.js';
import { LocalInference, type Framing } from './inference/local.js';
import { Store } from './storage/store.js';
import { FieldProposals, type Source } from './tools/fields.js';
import { Records, type AiTask, type Entity } from './tools/records.js';

// Measures how often the assistant proposes the right value for a field on a fixed set of records.
// It runs the same code as a real proposal (prompt, schema, checks), against an in-memory Core.
type Expect = { status: 'proposed'; value: string; warnings: { field: string; expected: string }[] } | { status: 'insufficient' };
type Case = { id: string; record: Record<string, unknown>; expect: Expect };
type Suite = { name: string; about: string; entity: Entity; task: AiTask; sources: Source[]; cases: Case[] };
type Verdict = 'correct' | 'wrong' | 'guessed' | 'refused-right' | 'refused-wrong' | 'rejected';
type Outcome = { id: string; suite: string; expected: string; got: string; verdict: Verdict; checks: 'right' | 'missed' | 'extra' | null };

const PASSED: Verdict[] = ['correct', 'refused-right'];
const LABEL: Record<Verdict, string> = {
  correct: 'đúng', wrong: 'sai', guessed: 'đoán khi thiếu dữ kiện', 'refused-right': 'từ chối đúng',
  'refused-wrong': 'từ chối nhầm', rejected: 'không có đầu ra hợp lệ',
};

export function judge(expect: Expect, got: { value: string | null; warnings: { field: string; expected: string }[] } | 'refused' | 'rejected'): { verdict: Verdict; checks: Outcome['checks'] } {
  if (got === 'rejected') return { verdict: 'rejected', checks: null };
  if (expect.status === 'insufficient') return { verdict: got === 'refused' ? 'refused-right' : 'guessed', checks: null };
  if (got === 'refused') return { verdict: 'refused-wrong', checks: null };
  const key = (w: { field: string; expected: string }) => `${w.field}=${w.expected}`;
  const want = new Set(expect.warnings.map(key)), have = new Set(got.warnings.map(key));
  const missed = [...want].some(x => !have.has(x)), extra = [...have].some(x => !want.has(x));
  const checks = missed ? 'missed' : extra ? 'extra' : 'right';
  return { verdict: got.value === expect.value ? 'correct' : 'wrong', checks };
}

async function run(suite: Suite, inference: LocalInference, state: Store, health: { answered: number; failed: number },
  report: (outcome: Outcome) => void) {
  for (const item of suite.cases) {
    // One owner per case keeps the limit on pending proposals out of the way.
    const owner = { issuer: 'https://benchmark.invalid', subject: item.id };
    const core = {
      async read(_owner: unknown, path: string) {
        return structuredClone(path.startsWith('/meta/') ? { ...suite.entity, aiTasks: { task: suite.task } } : { version: 0, status: 'open', ...item.record });
      },
    };
    const fields = new FieldProposals(new Records(core as never, state), core as never, state, inference, suite.sources);
    let got: Parameters<typeof judge>[1];
    let shown: string;
    try {
      const result = await fields.create(owner, { model: 'benchmark', entity: suite.entity.name, id: 1 }, 'task');
      health.answered++;
      if (result.insufficient || !result.proposal) { got = 'refused'; shown = 'từ chối'; }
      else {
        const s = result.proposal.snapshot;
        got = { value: String(s.value), warnings: (s.warnings ?? []).map((w: { field: string; expected: string }) => ({ field: w.field, expected: w.expected })) };
        shown = String(s.value);
      }
    } catch (error) {
      // An answer the checks refuse, a cut-off answer and a timeout count against the model once it has answered at all.
      // A model that has not answered the first cases is down, and the run stops there.
      const refused = error instanceof ApiError && error.status === 422;
      const silent = error instanceof ApiError && error.status === 503 && ['ERR_INFERENCE_OUTPUT', 'ERR_INFERENCE_REJECTED'].includes(error.errorCode);
      if (!refused && !silent) throw error;
      if (silent && health.answered === 0 && ++health.failed >= 3) throw error;
      if (refused) health.answered++;
      got = 'rejected'; shown = 'không có đầu ra hợp lệ';
    }
    const expected = item.expect.status === 'insufficient' ? 'từ chối' : item.expect.value;
    report({ id: item.id, suite: suite.name, expected, got: shown, ...judge(item.expect, got) });
  }
}

export async function main(argv: string[], env: NodeJS.ProcessEnv) {
  const option = (name: string) => { const at = argv.indexOf(name); return at < 0 ? undefined : argv[at + 1]; };
  const casesPath = option('--cases') ?? new URL('../benchmark/suites.json', import.meta.url).pathname;
  const only = option('--suite');
  const endpoint = env.ASSISTANT_INFERENCE_URL;
  if (!endpoint || !env.ASSISTANT_MODEL || !env.ASSISTANT_MODEL_REVISION) {
    console.error('Chưa có model để đo: đặt ASSISTANT_INFERENCE_URL, ASSISTANT_MODEL và ASSISTANT_MODEL_REVISION. Không có số nào được in.');
    return 2;
  }
  const raw = readFileSync(casesPath);
  const set = JSON.parse(raw.toString('utf8')) as { suites: Suite[] };
  const suites = set.suites.filter(suite => !only || suite.name === only);
  if (suites.length === 0) { console.error('Không có bộ mẫu nào khớp --suite.'); return 2; }
  const framingFile = env.ASSISTANT_PROMPT_FRAMING_FILE;
  const inference = new LocalInference({ endpoint, model: env.ASSISTANT_MODEL, revision: env.ASSISTANT_MODEL_REVISION,
    key: env.ASSISTANT_INFERENCE_KEY_FILE ? readFileSync(env.ASSISTANT_INFERENCE_KEY_FILE, 'utf8').trim() : '',
    framing: framingFile ? JSON.parse(readFileSync(framingFile, 'utf8')) as Framing : undefined,
    timeoutMs: Number(env.ASSISTANT_INFERENCE_TIMEOUT_MS ?? '120000'),
    maxInputBytes: Number(env.ASSISTANT_MAX_INPUT_BYTES ?? '6000'),
    maxOutputTokens: Number(env.ASSISTANT_MAX_OUTPUT_TOKENS ?? '256') });
  const dir = mkdtempSync(join(tmpdir(), 'dxos-benchmark-'));
  const state = new Store(join(dir, 'private/state.sqlite'), randomBytes(32));
  const outcomes: Outcome[] = [];
  const json = argv.includes('--json');
  const started = Date.now();
  const health = { answered: 0, failed: 0 };
  let printed = 0;
  try {
    for (const suite of suites) {
      await run(suite, inference, state, health, outcome => {
        outcomes.push(outcome);
        // Rows wait until the model has answered once, so a model that is down prints nothing.
        if (json || health.answered === 0) return;
        for (const row of outcomes.slice(printed)) {
          const checks = row.checks === 'missed' ? '; đối chiếu bỏ sót' : row.checks === 'extra' ? '; đối chiếu thừa' : '';
          console.log(`${row.id.padEnd(14)} ${row.expected.padEnd(16)} ${row.got.padEnd(24)} ${LABEL[row.verdict]}${checks}`);
        }
        printed = outcomes.length;
      });
    }
  } catch (error) {
    // The model never answered: say so and print no numbers.
    console.error(`Model không trả lời được (${error instanceof ApiError ? error.errorCode : 'ERR_INFERENCE'}); kiểm ASSISTANT_INFERENCE_URL và model đã chạy chưa. Không có số nào được in.`);
    return 2;
  } finally { state.close(); rmSync(dir, { recursive: true, force: true }); }

  const count = (verdict: Verdict) => outcomes.filter(x => x.verdict === verdict).length;
  const checked = outcomes.filter(x => x.checks !== null);
  const summary = {
    model: env.ASSISTANT_MODEL, revision: env.ASSISTANT_MODEL_REVISION, inferenceRevision: inference.revision,
    framing: framingFile ? 'native completion' : 'chat completions',
    set: { file: casesPath, sha256: createHash('sha256').update(raw).digest('hex') },
    cases: outcomes.length, seconds: Math.round((Date.now() - started) / 1000),
    correct: count('correct'), wrong: count('wrong'), guessed: count('guessed'),
    refusedRight: count('refused-right'), refusedWrong: count('refused-wrong'), rejected: count('rejected'),
    passed: outcomes.filter(x => PASSED.includes(x.verdict)).length,
    checks: { compared: checked.length, right: checked.filter(x => x.checks === 'right').length,
      missed: checked.filter(x => x.checks === 'missed').length, extra: checked.filter(x => x.checks === 'extra').length },
    bySuite: Object.fromEntries(suites.map(s => { const own = outcomes.filter(x => x.suite === s.name);
      return [s.name, { cases: own.length, passed: own.filter(x => PASSED.includes(x.verdict)).length }]; })),
  };
  if (json) { console.log(JSON.stringify({ summary, outcomes }, null, 2)); return 0; }
  const pct = (n: number, of: number) => of === 0 ? '—' : `${Math.round((n / of) * 100)}%`;
  console.log(`\nModel: ${summary.model}, revision ${summary.revision} (${summary.framing}, ${summary.inferenceRevision.slice(0, 12)})`);
  console.log(`Bộ mẫu: ${summary.set.file}, sha256 ${summary.set.sha256.slice(0, 12)}, ${summary.cases} hồ sơ, ${summary.seconds} giây`);
  console.log(`Đúng ${summary.correct}, sai ${summary.wrong}, đoán khi thiếu dữ kiện ${summary.guessed}, từ chối đúng ${summary.refusedRight}, từ chối nhầm ${summary.refusedWrong}, không có đầu ra hợp lệ ${summary.rejected}`);
  console.log(`Đạt ${summary.passed}/${summary.cases} (${pct(summary.passed, summary.cases)})`);
  for (const [name, own] of Object.entries(summary.bySuite)) console.log(`  ${name}: ${own.passed}/${own.cases} (${pct(own.passed, own.cases)})`);
  console.log(`Đối chiếu trên ${summary.checks.compared} hồ sơ có đề xuất: đúng ${summary.checks.right}, bỏ sót ${summary.checks.missed}, thừa ${summary.checks.extra}`);
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) process.exitCode = await main(process.argv.slice(2), process.env);
