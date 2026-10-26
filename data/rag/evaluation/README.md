# RAG evaluation dataset (draft)

This directory contains the Phase 1 pilot set for retrieval evaluation.

Status: `draft`. The questions and qrels are prepared from the fixed Farm and
Lab corpora, but they are not an independent evaluation until a domain reviewer
checks every question and its chunk labels without seeing retrieval rankings.

Files:

- `questions.jsonl`: 36 Farm/Lab QA questions and 6 diagnostic retrieval queries.
- `qrels.draft.tsv`: draft TREC qrels. Unlisted chunks have implicit label `0`.
- `metadata.json`: corpus versions, scope and review status.

Qrel labels are `0` (unsupported), `1` (partial support), and `2` (direct
support). The draft qrels intentionally stay separate from any official score.
