# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0

from collections import Counter
from functools import lru_cache
from math import exp, sqrt
import re
import unicodedata
from zlib import crc32

from noise import unaccent

REFUSE = "TU_CHOI"
THRESHOLD_TOLERANCE = 1e-12
FORMAT_VERSION = 1
FEATURES = {
    "revision": 1, "dimension": 8192, "hash": "crc32-utf8",
    "character_range": [2, 5], "word_range": [1, 2],
    "scales": {"accented": 0.8, "unaccented": 0.8, "word": 1.6},
    "normalization": "NFC, lowercase, token boundary padding, L2",
    "clip_chars_per_field": 2000,
}
WORDS = re.compile(r"[^\W_]+", re.UNICODE)
SENTENCES = re.compile(r"[^.!?;\n]+")
FILLER = set("em e anh chi minh toi xin cho nho giup de va voi vs a ah oi nhe cua la co duoc dc "
             "thi ma nay do cac nhung mot khi da dang se bi k ko khong".split())
COMMAND = (
    r"\b(?:bo qua (?:moi |tat ca )?(?:huong dan|tieu chi|quy tac)|"
    r"ignore|output|bot (?:set|chon|skip|tra)|nhan phai la|"
    r"(?:he thong|chuong trinh|may) (?:hay |luon )?(?:chon|dien|gan|tra ve)|"
    r"luon chon)\b|^\s*(?:xin |vui long )?chi (?:in|can go|in ra)\b"
)


# Fold text to lowercase unaccented words separated by spaces.
def normalized(text):
    return " ".join(WORDS.findall(unaccent(unicodedata.normalize("NFC", text)).lower()))


# Build the command filter from generic phrases and the model's normalized labels.
@lru_cache(maxsize=64)
def command_pattern(labels):
    names = "|".join(filter(None, map(normalized, labels)))
    choose = rf"|^\s*chon (?:muc |nhan )?(?:{names})\b" if names else ""
    return re.compile(COMMAND + choose)


def input_fields(row):
    return tuple(unicodedata.normalize("NFC", row.get(name, ""))
                 for name in ("title", "text"))


# Keep the segments that do not match the command filter.
def usable_segments(field, command):
    return [match for match in SENTENCES.finditer(field) if not command.search(normalized(match[0]))]


# Hash character and word n-grams into a normalized vector with optional source spans.
def extract(fields, settings, command, with_spans=False):
    counts = Counter()
    spans = {}
    dimension = settings["dimension"]
    scales = settings["scales"]
    sizes = range(settings["character_range"][0], settings["character_range"][1] + 1)
    fold_words = settings.get("fold_words", False)

    for field_number, field in enumerate(fields):
        for segment in usable_segments(field, command):
            tokens = list(WORDS.finditer(segment[0]))
            lowered = [token[0].lower() for token in tokens]
            folded = [unaccent(word) for word in lowered]
            word_tokens = folded if fold_words else lowered
            for i, token in enumerate(tokens):
                start = segment.start() + token.start()
                end = segment.start() + token.end()
                local = Counter()
                for prefix, word in ((b"a", lowered[i]), (b"u", folded[i])):
                    padded = " " + word + " "
                    scale = scales["accented" if prefix == b"a" else "unaccented"]
                    for size in sizes:
                        for origin in range(len(padded) - size + 1):
                            bucket = crc32(prefix + padded[origin:origin + size].encode()) % dimension
                            local[bucket] += scale
                for size in range(settings["word_range"][0], settings["word_range"][1] + 1):
                    if i + size > len(tokens):
                        break
                    key = " ".join(word_tokens[i:i + size]).encode()
                    bucket = crc32(b"w" + key) % dimension
                    if size == 1:
                        local[bucket] += scales["word"]
                    else:
                        counts[bucket] += scales["word"]
                        if with_spans:
                            last = segment.start() + tokens[i + size - 1].end()
                            spans[field_number, start, last] = {bucket: scales["word"]}
                counts.update(local)
                if with_spans:
                    spans[field_number, start, end] = local
    norm = sqrt(sum(value * value for value in counts.values())) or 1.0
    return {bucket: value / norm for bucket, value in counts.items()}, spans


def probabilities(logits):
    peak = max(logits)
    weights = [exp(value - peak) for value in logits]
    total = sum(weights)
    return [value / total for value in weights]


def score(model, vector):
    return probabilities([
        bias + sum(weights[bucket] * value for bucket, value in vector.items())
        for bias, weights in zip(model["bias"], model["weights"], strict=True)
    ])


# Rank labels by probability and refuse empty or oversized input.
def rank(model, row, with_spans=False):
    settings = model["features"]
    fields = input_fields(row)
    if any(len(field) > settings["clip_chars_per_field"] for field in fields):
        return REFUSE, 0.0, [], fields, {}
    vector, spans = extract(fields, settings, command_pattern(tuple(model["labels"])), with_spans)
    scores = score(model, vector)
    order = sorted(range(len(scores)), key=scores.__getitem__, reverse=True)
    winner = model["labels"][order[0]]
    if not vector:
        winner = REFUSE
    return winner, scores[order[0]], order, fields, spans


# Choose up to three non-overlapping phrases that support the top label.
def evidence(model, order, fields, spans):
    winner, runner_up = (model["weights"][index] for index in order[:2])
    scores = {span: sum((winner[bucket] - runner_up[bucket]) * count
                        for bucket, count in contributions.items())
              for span, contributions in spans.items()}
    legacy = [(value, *span) for span, value in scores.items() if value > 0]
    if not legacy:
        return []

    candidates = []
    for field, text in enumerate(fields):
        tokens = list(WORDS.finditer(text))
        folded = [unaccent(token[0].lower()) for token in tokens]
        for i, first in enumerate(tokens):
            for j in range(i + 1, min(i + 3, len(tokens))):
                last = tokens[j]
                gap = text[tokens[j - 1].end():last.start()]
                if not gap.isspace() or "\n" in gap:
                    break
                if all(word in FILLER for word in folded[i:j + 1]):
                    continue
                # Sum existing feature contributions contained in this phrase.
                value = 0.0
                for a in range(i, j + 1):
                    for b in range(a, j + 1):
                        value += scores.get((field, tokens[a].start(), tokens[b].end()), 0.0)
                if value > 0:
                    candidates.append((value, field, first.start(), last.end()))

    chosen = []
    seen = set()
    for _, field, start, end in sorted(candidates or legacy, reverse=True):
        phrase = fields[field][start:end]
        key = phrase.lower()
        if key in seen or any(field == f and start < b and a < end for f, a, b, _ in chosen):
            continue
        chosen.append((field, start, end, phrase))
        seen.add(key)
        if len(chosen) == 3:
            break
    return [phrase for _, _, _, phrase in chosen]


# Return the top label when it meets the confidence threshold and has supporting evidence.
# The threshold allows THRESHOLD_TOLERANCE so Python and Node decide alike on the threshold itself.
def predict(model, row):
    winner, confidence, order, fields, spans = rank(model, row, with_spans=True)
    phrases = []
    if winner != REFUSE and confidence >= model["threshold"] - THRESHOLD_TOLERANCE:
        phrases = evidence(model, order, fields, spans)
    return {"label": winner if phrases else REFUSE, "confidence": confidence, "evidence": phrases}
