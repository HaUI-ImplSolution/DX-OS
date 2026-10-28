# SPDX-License-Identifier: Apache-2.0
import unittest
from unittest.mock import patch

from app.rag.chat_search import (
    ChatSearch,
    allowed_for_audience,
    outside_scope,
    profiles,
    tokens,
)
from app.rag.safety import redact_medication_lines


def passages():
    return [
        dict(
            chunk_id=str(i),
            document_id=str(i),
            document_title=title,
            namespace="lab",
            document_version="v1",
            content=text,
            score=score,
            source_ids=["source"],
            chunk_index=0,
        )
        for i, (title, text, score) in enumerate(
            [
                ("Thư viện", "Quy định mượn sách và trả sách", 0.8),
                ("Wifi", "Dịch vụ hỗ trợ kết nối wifi", 0.7),
                ("Email", "Dịch vụ hỗ trợ email sinh viên", 0.6),
            ]
        )
    ]


class ChatSearchTest(unittest.TestCase):
    def test_chat_startup_without_cache_keeps_other_endpoints_available(self):
        import asyncio
        from app.rag.reranker import chat_lifespan

        async def start():
            async with chat_lifespan(None):
                pass

        with patch("app.rag.reranker._model", side_effect=OSError("Missing cache")):
            asyncio.run(start())

    def test_empty_redacted_results_are_removed_before_top_k_and_ranking(self):
        data = [{**p, "namespace": "farm"} for p in passages()]
        data[0]["content"] = "Phun thuốc theo liều."
        result = ChatSearch(reranker=lambda q, rows: [0.99, 0.9, 0.8]).rank(
            "Theo dõi lúa", data, top_k=2
        )
        self.assertEqual([p["rank"] for p in result["passages"]], [1, 2])
        self.assertTrue(all(p["chunk_id"] != "0" for p in result["passages"]))

    def test_empty_approved_corpus_is_selected_for_removal(self):
        import tempfile
        from dataclasses import replace
        from pathlib import Path
        from app.rag.ingestion_service import FARM_KNOWLEDGE_CORPUS, selected_corpora

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "empty.json"
            path.write_text("[]")
            empty = replace(FARM_KNOWLEDGE_CORPUS, data_file=path)
            with patch("app.rag.ingestion_service.FARM_KNOWLEDGE_CORPUS", empty):
                self.assertIn(empty, selected_corpora("farm"))

    def test_public_evidence_redacts_medication_lines(self):
        content = "Theo dõi ruộng thường xuyên.\nDùng thuốc Pretilachlor 2 ml/ha.\nGiữ nước phù hợp."
        self.assertEqual(
            redact_medication_lines(content),
            "Theo dõi ruộng thường xuyên.\nGiữ nước phù hợp.",
        )

    def test_chat_startup_loads_cached_ranker_before_serving(self):
        import asyncio
        from app.rag.reranker import chat_lifespan

        async def start():
            async with chat_lifespan(None):
                model.assert_called_once()

        with patch("app.rag.reranker._model") as model:
            asyncio.run(start())

    def test_normalization_keeps_numbers_and_handles_d_stroke(self):
        self.assertEqual(tokens("ĐẠO ôn 05 kg/ha"), ["dao", "on", "05", "kg", "ha"])

    def test_scope_allows_general_questions_but_blocks_clear_foreign_topics(self):
        self.assertFalse(outside_scope("Cần chuẩn bị những gì?", profiles()["lab"]))
        self.assertTrue(outside_scope("Lúa bị đạo ôn", profiles()["lab"]))
        self.assertFalse(
            outside_scope(
                "So sánh thiết bị wifi với ô tô trong bài hướng dẫn HaUI",
                profiles()["lab"],
            )
        )

    def test_scope_blocks_unrelated_categories_without_blocking_rice_climate(self):
        farm = profiles()["farm"]
        for question in (
            "Gợi ý phim hành động",
            "Đội bóng nào thắng?",
            "Nấu phở thế nào?",
            "Viết bài thơ",
            "Thời tiết Đà Nẵng ngày mai ra sao?",
        ):
            with self.subTest(question=question):
                self.assertTrue(outside_scope(question, farm))
        self.assertFalse(
            outside_scope("Thời tiết ảnh hưởng canh tác lúa thế nào?", farm)
        )

    def test_student_answers_exclude_teacher_management_source(self):
        profile = profiles()["lab"]
        source = ["SRC_HAUI_ITC_FARONICS"]
        self.assertFalse(
            allowed_for_audience("Dịch vụ cho sinh viên?", source, profile)
        )
        self.assertTrue(
            allowed_for_audience(
                "Giảng viên quản lý phòng máy thế nào?", source, profile
            )
        )
        self.assertTrue(
            allowed_for_audience(
                "Dịch vụ cho sinh viên?", ["SRC_HAUI_ITC_WIFI"], profile
            )
        )

    def test_reranking_can_recover_low_cosine_lexical_candidate(self):
        data = passages()
        data[2]["score"] = 0.1

        def reranker(query, rows):
            return [0.95 if p["document_title"] == "Email" else 0.1 for p in rows]

        result = ChatSearch(reranker=reranker).rank("Dịch vụ email", data)
        self.assertEqual(result["passages"][0]["document_title"], "Email")
        self.assertEqual(result["passages"][0]["score"], 0.1)
        self.assertGreater(result["passages"][0]["rerank_score"], 0.9)

    def test_reranker_failure_abstains_without_presenting_hybrid_as_reranked(self):
        def fail(*args):
            raise TimeoutError()

        result = ChatSearch(reranker=fail).rank("Dịch vụ", passages())
        self.assertEqual(result["reason_code"], "RERANK_UNAVAILABLE")
        self.assertEqual(result["passages"], [])
        self.assertEqual(result["strategy"], "hybrid_unavailable")

    def test_invalid_reranker_scores_abstain(self):
        result = ChatSearch(reranker=lambda *args: [float("nan")] * 3).rank(
            "Dịch vụ", passages()
        )
        self.assertEqual(result["scope_status"], "IN_SCOPE_NO_EVIDENCE")

    def test_rrf_uses_ranks_and_has_no_duplicate_chunk(self):
        result = ChatSearch(strategy="hybrid").rank("email", passages())
        fused = result["rankings"]["rrf"]
        self.assertEqual(len({p["chunk_id"] for p in fused}), len(fused))
        email = next(p for p in fused if p["document_title"] == "Email")
        self.assertAlmostEqual(email["rrf_score"], 1 / 63 + 1 / 61)

    def test_cached_index_reuses_text_and_invalidates_on_document_replacement(self):
        from types import SimpleNamespace
        from unittest.mock import Mock

        ChatSearch._cache.clear()
        engine = ChatSearch(strategy="bm25")
        documents = [
            dict(
                document_id="old",
                document_title="Hướng dẫn",
                namespace="lab",
                document_type="lab_knowledge",
                document_version="v1",
                source_ids=(),
            )
        ]
        db = Mock()
        db.execute.return_value.all.return_value = [
            SimpleNamespace(
                id="chunk",
                document_id="old",
                chunk_index=0,
                content="Nội dung cũ",
                page_number=None,
            )
        ]
        old = engine._index(db, documents)
        self.assertIs(old, engine._index(db, documents))
        self.assertEqual(db.execute.call_count, 1)
        changed = [{**documents[0], "document_id": "new"}]
        db.execute.return_value.all.return_value = [
            SimpleNamespace(
                id="new-chunk",
                document_id="new",
                chunk_index=0,
                content="Nội dung mới",
                page_number=None,
            )
        ]
        new = engine._index(db, changed)
        self.assertNotEqual(old[0][0]["content"], new[0][0]["content"])
        self.assertEqual(db.execute.call_count, 2)
        self.assertNotIn("Nội dung cũ", str(list(ChatSearch._cache)))

    def test_low_cosine_query_cannot_be_rescued_by_a_high_reranker_score(self):
        from unittest.mock import Mock

        reranker = Mock(return_value=[0.99] * 3)
        data = [{**p, "score": 0.2} for p in passages()]
        result = ChatSearch(reranker=reranker).rank("Câu không liên quan", data)
        self.assertEqual(result["scope_status"], "IN_SCOPE_NO_EVIDENCE")
        self.assertEqual(result["reason_code"], "LOW_RELEVANCE")
        reranker.assert_not_called()

    def test_index_limit_refuses_partial_corpus(self):
        from unittest.mock import Mock

        ChatSearch._cache.clear()
        db = Mock()
        db.execute.return_value.all.return_value = [None] * 20001
        with self.assertRaisesRegex(ValueError, "in-memory index limit"):
            ChatSearch()._index(db, [dict(document_id="doc", namespace="lab")])
        self.assertFalse(ChatSearch._cache)

    def test_unknown_namespace_never_embeds_or_queries(self):
        from unittest.mock import Mock

        db = Mock()
        embedder = Mock()
        with self.assertRaises(ValueError):
            ChatSearch(embedding_service=embedder).search(db, "test", "unknown")
        db.execute.assert_not_called()
        embedder.embed_text.assert_not_called()

    def test_database_transaction_is_released_before_reranking(self):
        from contextlib import contextmanager
        from types import SimpleNamespace
        from unittest.mock import Mock

        document = SimpleNamespace(
            id="doc",
            title="Hướng dẫn",
            version="v1",
            namespace="lab",
            document_type="lab_knowledge",
            disease_code=None,
            source="source",
        )

        class Database:
            active = False
            statements = []

            @contextmanager
            def begin(self):
                self.active = True
                try:
                    yield
                finally:
                    self.active = False

            def scalars(self, stmt):
                self.assert_active()
                return SimpleNamespace(all=lambda: [document])

            def assert_active(self):
                assert self.active

            def execute(self, stmt):
                self.assert_active()
                self.statements.append(str(stmt))
                return SimpleNamespace(
                    all=lambda: [SimpleNamespace(id="chunk", distance=0.2)]
                )

        db = Database()

        def reranker(*args):
            self.assertFalse(db.active)
            return [0.9]

        embedder = Mock()
        embedder.embed_text.return_value = [0.0] * 384
        data = [dict(passages()[0], chunk_id="chunk")]
        engine = ChatSearch(embedding_service=embedder, reranker=reranker)
        with patch.object(
            engine,
            "_index",
            return_value=(data, SimpleNamespace(get_scores=lambda q: [0]), "nội dung"),
        ):
            result = engine.search(db, "Câu hỏi chung", "lab")
        self.assertEqual(result["scope_status"], "ANSWERABLE")
        self.assertFalse(db.active)
        dense_sql = next(q for q in db.statements if "ORDER BY" in q)
        self.assertIn("LIMIT", dense_sql)

    def test_overview_keeps_multiple_documents_with_at_most_two_chunks_each(self):
        data = passages()
        data.extend(
            [{**data[0], "chunk_id": "extra-1"}, {**data[0], "chunk_id": "extra-2"}]
        )
        result = ChatSearch(reranker=lambda q, p: [0.9] * len(p)).rank(
            "Tổng quan các dịch vụ", data
        )
        self.assertEqual(len(result["passages"]), 4)
        self.assertLessEqual(
            sum(p["document_id"] == "0" for p in result["passages"]), 2
        )


class KnowledgeSearchHttpTest(unittest.TestCase):
    def test_search_is_authenticated_and_request_cannot_override_strategy(self):
        import os
        from types import SimpleNamespace
        from unittest.mock import Mock

        os.environ.setdefault(
            "DATABASE_URL", "postgresql+psycopg://test:test@localhost/test"
        )
        from fastapi.testclient import TestClient

        from app.core.config import get_settings
        from app.db.session import get_db
        from app.main import create_app

        app = create_app()
        app.dependency_overrides[get_settings] = lambda: SimpleNamespace(
            ai_service_api_key="test-service-key"
        )
        app.dependency_overrides[get_db] = lambda: Mock()
        client = TestClient(app)
        envelope = dict(
            question="Câu hỏi chung",
            namespace="lab",
            scope_status="IN_SCOPE_NO_EVIDENCE",
            reason_code="NO_EVIDENCE",
            strategy="hybrid_rerank",
            passages=[],
        )
        body = dict(query="Câu hỏi chung", namespace="lab", top_k=4)
        with patch.object(ChatSearch, "search", return_value=envelope) as search:
            self.assertEqual(
                client.post("/api/v1/knowledge/search", json=body).status_code, 401
            )
            search.assert_not_called()
            headers = {"X-Service-Api-Key": "test-service-key"}
            response = client.post(
                "/api/v1/knowledge/search", json=body, headers=headers
            )
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json()["scope_status"], "IN_SCOPE_NO_EVIDENCE")
            self.assertEqual(
                client.post(
                    "/api/v1/knowledge/search",
                    json={**body, "strategy": "legacy"},
                    headers=headers,
                ).status_code,
                422,
            )
