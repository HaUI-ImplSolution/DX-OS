// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { crc32 } from 'node:zlib';

export type Settings = {
  revision: number; dimension: number; hash: string; normalization: string; fold_words?: boolean;
  character_range: [number, number]; word_range: [number, number]; clip_chars_per_field: number;
  scales: { accented: number; unaccented: number; word: number };
};

type Counts = Map<number, number>;
type Span = { field: number; start: number; end: number; counts: Counts };

const words = /[\p{L}\p{N}]+/gu;
const sentences = /[^.!?;\n]+/g;
const filler = new Set(('em e anh chi minh toi xin cho nho giup de va voi vs a ah oi nhe cua la co duoc dc '
  + 'thi ma nay do cac nhung mot khi da dang se bi k ko khong').split(' '));
const alphanumeric = String.raw`[\p{L}\p{N}_]`;
const boundary = `(?:(?<!${alphanumeric})(?=${alphanumeric})|(?<=${alphanumeric})(?!${alphanumeric}))`;
const pattern = (source: string) => new RegExp(source.replaceAll('\\b', boundary), 'u');
const command = (
  /\b(?:bo qua (?:moi |tat ca )?(?:huong dan|tieu chi|quy tac)|ignore|output|bot (?:set|chon|skip|tra)|nhan phai la|(?:he thong|chuong trinh|may) (?:hay |luon )?(?:chon|dien|gan|tra ve)|luon chon)\b/.source
  + '|'
  + /^\s*(?:xin |vui long )?chi (?:in|can go|in ra)\b/.source
);

function unaccent(text: string) {
  return text.normalize('NFD').replace(/\p{M}/gu, mark => {
    // Canonical ordering moves nonzero classes past at least one of the class 240 and 1 probes.
    const before = '\u0345' + mark, after = mark + '\u0334';
    return before.normalize('NFD') !== before || after.normalize('NFD') !== after ? '' : mark;
  }).replace(/[đĐ]/g, letter => letter === 'đ' ? 'd' : 'D');
}

// Fold text to lowercase unaccented words separated by spaces.
export function normalized(text: string) {
  return (unaccent(text.normalize('NFC')).toLowerCase().match(words) ?? []).join(' ');
}

// Build the command filter from generic phrases and the model's normalized labels.
export function commandPattern(labels: readonly string[]): RegExp {
  const names = labels.map(normalized).filter(Boolean).join('|');
  const choose = names ? String.raw`|^\s*chon (?:muc |nhan )?(?:${names})\b` : '';
  return pattern(command + choose);
}

// Keep the segments that do not match the command filter.
function segments(field: string, filter: RegExp) {
  return [...field.matchAll(sentences)].filter(segment => !filter.test(normalized(segment[0])));
}

function add(counts: Counts, bucket: number, value: number) {
  counts.set(bucket, (counts.get(bucket) ?? 0) + value);
}

// Hash character and word n-grams into a normalized vector and record their source spans.
export function extract(fields: readonly string[], settings: Settings, filter: RegExp) {
  const counts: Counts = new Map(), spans: Span[] = [];
  const { dimension, scales, character_range: [charMin, charMax], word_range: [wordMin, wordMax] } = settings;
  fields.forEach((field, fieldIndex) => {
    for (const segment of segments(field, filter)) {
      const tokens = [...segment[0].matchAll(words)];
      const lowered = tokens.map(token => token[0].toLowerCase());
      const folded = lowered.map(unaccent);
      const wordTokens = settings.fold_words ? folded : lowered;
      tokens.forEach((token, index) => {
        const start = segment.index + token.index;
        const end = start + token[0].length;
        const local: Counts = new Map();
        for (const [prefix, word, scale] of [['a', lowered[index]!, scales.accented],
          ['u', folded[index]!, scales.unaccented]] as const) {
          const padded = [' ', ...word, ' '];
          for (let size = charMin; size <= charMax; size++) {
            for (let origin = 0; origin + size <= padded.length; origin++) {
              add(local, crc32(prefix + padded.slice(origin, origin + size).join('')) % dimension, scale);
            }
          }
        }
        for (let size = wordMin; size <= wordMax && index + size <= tokens.length; size++) {
          const bucket = crc32('w' + wordTokens.slice(index, index + size).join(' ')) % dimension;
          if (size === 1) add(local, bucket, scales.word);
          else {
            add(counts, bucket, scales.word);
            const last = tokens[index + size - 1]!;
            spans.push({ field: fieldIndex, start, end: segment.index + last.index + last[0].length,
              counts: new Map([[bucket, scales.word]]) });
          }
        }
        for (const [bucket, value] of local) add(counts, bucket, value);
        spans.push({ field: fieldIndex, start, end, counts: local });
      });
    }
  });
  let squared = 0;
  for (const value of counts.values()) squared += value * value;
  const norm = Math.sqrt(squared) || 1;
  for (const [bucket, value] of counts) counts.set(bucket, value / norm);
  return { vector: counts, spans };
}

// Choose up to three non-overlapping phrases that support the top label.
export function evidence(fields: readonly string[], spans: Span[], best: number[], next: number[]) {
  const scores = new Map<string, number>();
  const legacy = spans.map(({ field, start, end, counts }) => {
    let contribution = 0;
    for (const [bucket, count] of counts) contribution += (best[bucket]! - next[bucket]!) * count;
    scores.set(`${field}:${start}:${end}`, contribution);
    return { field, start, end, contribution };
  }).filter(span => span.contribution > 0);
  if (!legacy.length) return [];

  const candidates: typeof legacy = [];
  fields.forEach((text, field) => {
    const tokens = [...text.matchAll(words)].map(token => ({
      start: token.index, end: token.index + token[0].length, folded: unaccent(token[0].toLowerCase()),
    }));
    tokens.forEach((first, i) => {
      for (let j = i + 1; j < Math.min(i + 3, tokens.length); j++) {
        const last = tokens[j]!;
        const gap = text.slice(tokens[j - 1]!.end, last.start);
        if (!/^[\p{White_Space}\u001c-\u001f]+$/u.test(gap) || gap.includes('\n')) break;
        if (tokens.slice(i, j + 1).every(token => filler.has(token.folded))) continue;
        // Sum existing feature contributions contained in this phrase.
        let contribution = 0;
        for (let a = i; a <= j; a++) {
          for (let b = a; b <= j; b++) {
            contribution += scores.get(`${field}:${tokens[a]!.start}:${tokens[b]!.end}`) ?? 0;
          }
        }
        if (contribution > 0) candidates.push({ field, start: first.start, end: last.end, contribution });
      }
    });
  });

  const ranked = (candidates.length ? candidates : legacy).sort((a, b) =>
    b.contribution - a.contribution || b.field - a.field || b.start - a.start || b.end - a.end);
  const chosen: { field: number; start: number; end: number; phrase: string }[] = [];
  const seen = new Set<string>();
  for (const { field, start, end } of ranked) {
    const phrase = fields[field]!.slice(start, end), key = phrase.toLowerCase();
    if (seen.has(key) || chosen.some(span => span.field === field && start < span.end && span.start < end)) continue;
    chosen.push({ field, start, end, phrase });
    seen.add(key);
    if (chosen.length === 3) break;
  }
  return chosen.map(span => span.phrase);
}
