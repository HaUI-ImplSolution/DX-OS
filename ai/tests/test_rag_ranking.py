# SPDX-License-Identifier: Apache-2.0
import types
from pathlib import Path

import pytest

from app.rag import embedding_service, ingestion_service
from app.rag.retriever import RAGRetriever, _topic_phrases


class FakeEmbeddings:
    def __init__(self, *_):
        self.texts: list[str] = []

    def embed_text(self, _text):
        return [0.0] * 384

    def embed_texts(self, texts):
        self.texts.extend(texts)
        return [[0.0] * 384 for _ in texts]


class FakeDb:
    def __init__(self, rows):
        self.rows = rows

    def execute(self, _statement):
        return types.SimpleNamespace(all=lambda: self.rows)


def row(title, code, distance, content="nội dung"):
    document = types.SimpleNamespace(
        title=title, version="v1", source="IRRI", disease_code=code
    )
    chunk = types.SimpleNamespace(
        id=f"chunk-{code}-{distance}",
        document_id=f"doc-{code}",
        document=document,
        chunk_index=0,
        content=content,
        page_number=None,
    )
    return chunk, distance


ROWS = [
    row("Bệnh đạo ôn trên lúa", "Rice___Blast", 0.30),
    row("Bệnh khô vằn trên lúa", "Rice___Sheath_blight", 0.40),
    row("Bệnh than lá lúa (leaf smut)", "Rice___Leaf_smut", 0.45),
]


@pytest.mark.parametrize(
    "title,expected",
    [
        ("Bệnh đạo ôn trên lúa", ["đạo ôn"]),
        ("Bệnh khô vằn trên lúa", ["khô vằn"]),
        ("Bệnh than lá lúa (leaf smut)", ["than lá", "leaf smut"]),
        ("Lá lúa không thấy triệu chứng bệnh", ["lá lúa không thấy triệu chứng bệnh"]),
    ],
)
def test_topic_phrases_come_from_the_document_title(title, expected):
    assert _topic_phrases(title) == expected


def retrieve(question, top_k=3):
    retriever = RAGRetriever(embedding_service=FakeEmbeddings())
    return retriever.retrieve(FakeDb(ROWS), question, top_k=top_k)


def test_passage_of_the_disease_the_question_names_comes_first():
    results = retrieve("Làm sao nhận biết bệnh khô vằn?")

    assert results[0]["disease_code"] == "Rice___Sheath_blight"
    assert results[0]["score"] == pytest.approx(0.60)


def test_vector_order_is_kept_when_the_question_names_no_disease():
    results = retrieve("Lúa có vết bệnh trên lá thì làm gì?")

    assert [item["disease_code"] for item in results] == [
        "Rice___Blast",
        "Rice___Sheath_blight",
        "Rice___Leaf_smut",
    ]


def test_result_is_cut_to_top_k_and_hides_the_ranking_key():
    results = retrieve("bệnh than lá", top_k=1)

    assert len(results) == 1
    assert results[0]["disease_code"] == "Rice___Leaf_smut"
    assert "_rank" not in results[0]


def test_ingest_embeds_the_title_with_every_chunk_and_stores_the_chunk_alone(monkeypatch):
    embeddings = FakeEmbeddings()
    monkeypatch.setattr(embedding_service, "EmbeddingService", lambda: embeddings)
    corpus = ingestion_service.Corpus(
        namespace="farm",
        document_type="disease_knowledge",
        data_file=Path("unused.json"),
        registry_file=None,
        version="test",
        code_field="code",
        title_field="title",
        required_fields=frozenset(),
        code_is_disease_code=True,
        lock_key=1,
        label="test",
    )
    monkeypatch.setattr(
        ingestion_service,
        "load_corpus",
        lambda _corpus: [
            {
                "code": "Rice___Blast",
                "title": "Bệnh đạo ôn trên lúa",
                "content": "Vết thoi, tâm trắng xám.\nRìa nâu đỏ.",
                "provenance": {"source_ids": ["IRRI"]},
            }
        ],
    )
    db = types.SimpleNamespace(
        execute=lambda *_: None,
        scalars=lambda *_: types.SimpleNamespace(all=lambda: []),
        add=lambda _item: stored.append(_item),
        flush=lambda: None,
        commit=lambda: None,
    )
    stored = []

    ingestion_service._ingest_corpus(db, corpus)

    assert embeddings.texts == ["Bệnh đạo ôn trên lúa\nVết thoi, tâm trắng xám.\nRìa nâu đỏ."]
    chunks = [item for item in stored if hasattr(item, "chunk_index")]
    assert [chunk.content for chunk in chunks] == ["Vết thoi, tâm trắng xám.\nRìa nâu đỏ."]
