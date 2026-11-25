// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { createHash } from 'node:crypto';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { commandPattern, evidence, extract, type Settings } from './features.js';

const refusal = 'TU_CHOI';
const THRESHOLD_TOLERANCE = 1e-12;
const modelFile = /^([a-z][a-z0-9_]{0,39})\.json$/;
const defaultDirectory = fileURLToPath(new URL('../../classifiers/', import.meta.url));

type Weights = {
  format_version: number; task: string; labels: string[]; weights: number[][]; bias: number[];
  features: Settings; threshold: number;
};
type Prediction = { label: string | null; probability: number; evidence: string[] };

function validRange(range: unknown): boolean {
  return Array.isArray(range) && range.length === 2 &&
    range.every(n => Number.isSafeInteger(n) && n > 0) && range[0] <= range[1];
}

function validWeights(model: Weights, name: string): boolean {
  const s = model?.features;
  return model?.format_version === 1 && model.task === name &&
    Array.isArray(model.labels) && model.labels.length >= 2 && model.labels.includes(refusal) &&
    model.labels.every(label => typeof label === 'string' && label.trim() !== '') &&
    new Set(model.labels).size === model.labels.length &&
    Number.isFinite(model.threshold) && model.threshold >= 0 && model.threshold <= 1 &&
    s?.revision === 1 && s.hash === 'crc32-utf8' &&
    s.normalization === 'NFC, lowercase, token boundary padding, L2' &&
    Number.isSafeInteger(s.dimension) && s.dimension > 0 &&
    Number.isSafeInteger(s.clip_chars_per_field) && s.clip_chars_per_field > 0 &&
    validRange(s.character_range) && validRange(s.word_range) &&
    (s.fold_words === undefined || typeof s.fold_words === 'boolean') &&
    [s.scales?.accented, s.scales?.unaccented, s.scales?.word].every(n => Number.isFinite(n) && n > 0) &&
    Array.isArray(model.bias) && model.bias.length === model.labels.length && model.bias.every(Number.isFinite) &&
    Array.isArray(model.weights) && model.weights.length === model.labels.length &&
    model.weights.every(row => Array.isArray(row) && row.length === s.dimension && row.every(Number.isFinite));
}

export class Classifier {
  readonly labels: readonly string[];
  private readonly command: RegExp;

  private constructor(private readonly model: Weights, readonly revision: string) {
    this.labels = Object.freeze(model.labels.filter(label => label !== refusal));
    this.command = commandPattern(model.labels);
  }

  // Validate the weights and use their SHA-256 as the classifier revision.
  static load(path: string, name: string): Classifier {
    const bytes = readFileSync(path);
    const model = JSON.parse(bytes.toString('utf8')) as Weights;
    if (!validWeights(model, name)) throw new Error('Invalid classifier weights');
    return new Classifier(model, createHash('sha256').update(bytes).digest('hex'));
  }

  // Score the labels and return the best one when it meets the threshold and has evidence.
  // The threshold allows THRESHOLD_TOLERANCE so Node and Python decide alike on the threshold itself.
  predict(input: readonly string[]): Prediction {
    const model = this.model;
    const fields = input.map(field => field.normalize('NFC'));
    if (fields.some(field => [...field].length > model.features.clip_chars_per_field))
      return { label: null, probability: 0, evidence: [] };
    const { vector, spans } = extract(fields, model.features, this.command);
    const logits = model.weights.map((weights, index) => {
      let score = 0;
      for (const [bucket, value] of vector) score += weights[bucket]! * value;
      return model.bias[index]! + score;
    });
    const peak = Math.max(...logits);
    const scores = logits.map(logit => Math.exp(logit - peak));
    const total = scores.reduce((sum, score) => sum + score, 0);
    const order = scores.map((score, index) => [score / total, index] as const)
      .sort(([a], [b]) => b - a);
    const [probability, winner] = order[0]!;
    const label = model.labels[winner]!;
    const insufficient = { label: null, probability, evidence: [] };
    if (!vector.size || label === refusal || probability < model.threshold - THRESHOLD_TOLERANCE) return insufficient;
    const best = model.weights[winner]!, next = model.weights[order[1]![1]]!;
    const phrases = evidence(fields, spans, best, next);
    return phrases.length ? { label, probability, evidence: phrases } : insufficient;
  }
}

// Load valid classifier files under their task names.
export function loadClassifiers(directory = defaultDirectory): Map<string, Classifier> {
  const classifiers = new Map<string, Classifier>();
  let entries: string[];
  try { entries = readdirSync(directory).sort(); }
  catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') console.warn('Could not read the classifier directory');
    return classifiers;
  }
  for (const entry of entries) {
    const name = modelFile.exec(entry)?.[1];
    if (!name) continue;
    try { classifiers.set(name, Classifier.load(join(directory, entry), name)); }
    catch (error) {
      console.warn(`Classifier ${name} is unavailable (${(error as NodeJS.ErrnoException).code ?? 'invalid weights'})`);
    }
  }
  return classifiers;
}
