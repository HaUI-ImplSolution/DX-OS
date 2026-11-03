# SPDX-License-Identifier: AGPL-3.0-only
import json

import pytest

from app.rag.chunker import chunk_text
from app.rag.ingestion_service import Corpus, load_corpus

SOURCES = [
    {"source_id": "s1", "title": "Guide", "url": "https://example.org/guide", "license": "CC-BY-4.0"}
]
DOCUMENT = {"code": "a-1", "title": "Đăng ký", "content": "Nội dung", "source_ids": ["s1"]}


def corpus(tmp_path, documents, sources=SOURCES, namespace="default"):
    documents_file = tmp_path / "documents.json"
    sources_file = tmp_path / "sources.json"
    documents_file.write_text(json.dumps(documents), encoding="utf-8")
    sources_file.write_text(json.dumps(sources), encoding="utf-8")
    return Corpus(namespace, "document", "v1", documents_file, sources_file)


def test_a_valid_corpus_loads(tmp_path):
    assert load_corpus(corpus(tmp_path, [DOCUMENT])) == [DOCUMENT]


@pytest.mark.parametrize(
    "change, message",
    [
        ({"source_ids": ["missing"]}, "unknown sources"),
        ({"content": "  "}, "empty content"),
        ({"title": ""}, "invalid title"),
        ({"code": ""}, "invalid code"),
    ],
)
def test_an_invalid_document_is_refused(tmp_path, change, message):
    with pytest.raises(ValueError, match=message):
        load_corpus(corpus(tmp_path, [{**DOCUMENT, **change}]))


def test_duplicate_codes_are_refused(tmp_path):
    with pytest.raises(ValueError, match="Duplicate code"):
        load_corpus(corpus(tmp_path, [DOCUMENT, {**DOCUMENT, "code": "A-1"}]))


def test_a_source_without_a_license_is_refused(tmp_path):
    sources = [{key: value for key, value in SOURCES[0].items() if key != "license"}]
    with pytest.raises(ValueError, match="missing fields: license"):
        load_corpus(corpus(tmp_path, [DOCUMENT], sources))


def test_the_namespace_is_checked(tmp_path):
    with pytest.raises(ValueError, match="namespace"):
        load_corpus(corpus(tmp_path, [DOCUMENT], namespace="Bad Name"))


def test_chunks_stay_within_the_size_limit():
    text = "\n".join(f"Dòng số {i} " + "x" * 80 for i in range(20))

    chunks = chunk_text(text, max_chars=350)

    assert len(chunks) > 1
    assert all(len(chunk) <= 350 for chunk in chunks)
