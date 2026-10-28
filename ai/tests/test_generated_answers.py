# SPDX-License-Identifier: Apache-2.0
"""Runnable checks for generated wording and its evidence boundary."""

import copy
import json
import os
import unittest
from types import SimpleNamespace
from unittest.mock import patch

os.environ.setdefault(
    "DATABASE_URL", "postgresql+psycopg://test:test@localhost:5432/test"
)

from app.rag.answer_contract import KnowledgeAnswerRequest
from app.rag.prompt_builder import build_answer_messages
from app.rag.rag_service import synthesize_answer

PASSAGES = [
    {
        "content": "Triệu chứng: Lá có vết bệnh. Phòng ngừa: Giữ đồng ruộng thông thoáng.",
        "document_title": "Bệnh đạo ôn",
        "source_ids": ["src_1"],
    }
]


class Model:
    def __init__(self, draft=None, error=None):
        self.draft = draft or {
            "summary": "Tài liệu mô tả triệu chứng và cách phòng ngừa đạo ôn.",
            "steps": [],
            "note": None,
            "source_ids": ["src_1"],
        }
        self.error = error
        self.calls = []

    def invoke(self, messages, **_kwargs):
        self.calls.append(messages)
        if self.error:
            raise self.error
        return SimpleNamespace(content=json.dumps(self.draft, ensure_ascii=False))


class GeneratedAnswerChecks(unittest.TestCase):
    def setUp(self):
        registry = patch(
            "app.rag.rag_service.load_source_registry",
            return_value={"src_1": {"title": "Test source"}},
        )
        registry.start()
        self.addCleanup(registry.stop)

    def test_generated_wording_does_not_replace_evidence(self):
        original = copy.deepcopy(PASSAGES)
        model = Model()
        answer = synthesize_answer("Đạo ôn là gì?", PASSAGES, "farm_qa", llm=model)
        self.assertEqual(answer.mode, "generated")
        self.assertEqual(answer.source_ids, ["src_1"])
        self.assertEqual(answer.prompt_version, "farm_qa-v2")
        self.assertEqual(PASSAGES, original)
        self.assertEqual(len(model.calls), 1)

    def test_no_evidence_or_medication_question_never_calls_model(self):
        for question, passages, mode in [
            ("Đạo ôn?", [], "insufficient_context"),
            ("Liều thuốc bao nhiêu?", PASSAGES, "extractive"),
        ]:
            model = Model()
            answer = synthesize_answer(question, passages, "farm_qa", llm=model)
            self.assertEqual(answer.mode, mode)
            self.assertFalse(model.calls)

    def test_absence_of_symptoms_cannot_use_unrelated_disease_advice(self):
        model = Model()
        answer = synthesize_answer(
            "Nếu ruộng lúa chưa thấy dấu hiệu thuộc các lớp bệnh trong tập ảnh thì nên theo dõi và xử lý thế nào?",
            PASSAGES,
            "farm_qa",
            llm=model,
        )
        self.assertEqual(answer.reason, "insufficient_evidence")
        self.assertFalse(model.calls)
        control = Model()
        answer = synthesize_answer(
            "Bệnh đạo ôn chưa thấy triệu chứng rõ thì tài liệu mô tả thế nào?",
            PASSAGES,
            "farm_qa",
            llm=control,
        )
        self.assertEqual(answer.mode, "generated")
        self.assertEqual(len(control.calls), 1)

    def test_personal_diagnosis_requests_are_not_disease_knowledge_questions(self):
        for question, allowed in [
            ("Lúa của tôi bị vàng lá và lùn. Vậy chắc chắn là bệnh gì?", False),
            ("Ruộng của mình có phải bị đạo ôn không?", False),
            ("Tôi quan sát lúa bị lùn, cây lúa đang bị bệnh gì?", False),
            ("Ruộng của tôi bị đạo ôn, tài liệu mô tả triệu chứng thế nào?", True),
            ("Đạo ôn là bệnh gì?", True),
        ]:
            with self.subTest(question=question):
                model = Model()
                answer = synthesize_answer(question, PASSAGES, "farm_qa", llm=model)
                self.assertEqual(bool(model.calls), allowed)
                if not allowed:
                    self.assertEqual(answer.reason, "insufficient_evidence")
                    self.assertEqual(answer.summary, "")

    def test_invalid_or_unsupported_drafts_fall_back(self):
        valid = Model().draft
        changes = [
            {"source_ids": ["invented"]},
            {"summary": "中文回答"},
            {"summary": "Phun thuốc với liều 5 ml/l."},
            {"summary": "Ruộng của bạn chắc chắn bị bệnh."},
            {"summary": "Ảnh cho thấy có vết bệnh nặng."},
            {"summary": "Tỷ lệ bệnh là 99%."},
            {"summary": "https://invented.test"},
            {"steps": ["Một ý"] * 4},
            {"mode": "generated"},
            {"summary": "a" * 351},
        ]
        for change in changes:
            with self.subTest(change=change):
                answer = synthesize_answer(
                    "Đạo ôn là gì?", PASSAGES, "farm_qa", llm=Model({**valid, **change})
                )
                self.assertEqual(answer.mode, "extractive")
                self.assertIsNone(answer.model_name)

    def test_cross_disease_draft_is_rejected_despite_valid_source(self):
        context = {
            "disease_code": "Rice___Blast",
            "disease_name": "Bệnh đạo ôn trên lúa",
        }
        draft = {
            **Model().draft,
            "summary": "Kết quả là bệnh đốm nâu với các vết tròn trên lá.",
        }
        answer = synthesize_answer(
            "Giải thích bệnh.",
            PASSAGES,
            "farm_diagnosis",
            context=context,
            llm=Model(draft),
        )
        self.assertEqual(answer.mode, "extractive")
        self.assertEqual(answer.reason, "conflicting_output")
        self.assertEqual(answer.summary, "")

    def test_comparison_cannot_supply_target_symptoms(self):
        context = {
            "disease_code": "Rice___Blast",
            "disease_name": "Bệnh đạo ôn trên lúa",
        }
        passages = [
            {
                **PASSAGES[0],
                "content": "Triệu chứng: Vết bệnh thường hình thoi; khác đốm nâu thường tròn hơn.",
            }
        ]
        for summary in [
            "Bệnh đạo ôn thường có vết tròn trên lá.",
            "Bệnh đạo ôn luôn có vết hình thoi.",
        ]:
            with self.subTest(summary=summary):
                model = Model({**Model().draft, "summary": summary})
                answer = synthesize_answer(
                    "Giải thích bệnh.",
                    passages,
                    "farm_diagnosis",
                    context=context,
                    llm=model,
                )
                self.assertNotEqual(answer.mode, "generated")
                supplied = json.loads(model.calls[0][1][1])["evidence"][0]["content"]
                self.assertNotIn("đốm nâu", supplied)
        valid = Model(
            {
                **Model().draft,
                "summary": "Theo tài liệu, bệnh đạo ôn thường có vết bệnh hình thoi.",
            }
        )
        self.assertEqual(
            synthesize_answer(
                "Giải thích bệnh.",
                passages,
                "farm_diagnosis",
                context=context,
                llm=valid,
            ).mode,
            "generated",
        )

    def test_abstention_returns_no_model_content(self):
        for reason in ["insufficient_evidence", "out_of_scope", "requires_authority"]:
            with self.subTest(reason=reason):
                draft = {
                    "summary": "",
                    "steps": [],
                    "note": None,
                    "source_ids": [],
                    "abstain_reason": reason,
                }
                answer = synthesize_answer(
                    "Câu hỏi?", PASSAGES, "lab_qa", llm=Model(draft)
                )
                self.assertNotEqual(answer.mode, "generated")
                self.assertEqual(answer.reason, reason)
                self.assertEqual(answer.summary, "")
                self.assertEqual(answer.steps, [])
                self.assertIsNone(answer.note)
        invalid = {**draft, "abstain_reason": "untrusted_reason"}
        self.assertEqual(
            synthesize_answer(
                "Câu hỏi?", PASSAGES, "lab_qa", llm=Model(invalid)
            ).reason,
            "generation_failed",
        )

    def test_unstructured_uncertainty_is_not_a_generated_answer(self):
        model = Model(
            {
                **Model().draft,
                "summary": "Tôi không đủ thông tin để trả lời câu hỏi này.",
            }
        )
        answer = synthesize_answer("Câu hỏi?", PASSAGES, "lab_qa", llm=model)
        self.assertNotEqual(answer.mode, "generated")
        self.assertEqual(answer.summary, "")

    def test_conditional_language_does_not_alone_trigger_abstention(self):
        model = Model(
            {**Model().draft, "summary": "Bệnh đạo ôn có thể xuất hiện khi độ ẩm cao."}
        )
        passages = [
            {**PASSAGES[0], "content": "Bệnh đạo ôn có thể xuất hiện khi độ ẩm cao."}
        ]
        answer = synthesize_answer(
            "Đạo ôn xuất hiện khi nào?", passages, "farm_qa", llm=model
        )
        self.assertEqual(answer.mode, "generated")
        self.assertIsNone(answer.reason)

    def test_diagnosis_requires_canonical_context_before_model(self):
        for context in [
            {},
            {"disease_code": "unknown"},
            {"disease_code": "Rice___Blast", "disease_name": "Bệnh đốm nâu trên lúa"},
        ]:
            with self.subTest(context=context):
                model = Model()
                answer = synthesize_answer(
                    "Giải thích bệnh.",
                    PASSAGES,
                    "farm_diagnosis",
                    context=context,
                    llm=model,
                )
                self.assertNotEqual(answer.mode, "generated")
                self.assertFalse(model.calls)

    def test_farm_qa_cannot_switch_disease_or_compare_without_both_sources(self):
        wrong = Model({**Model().draft, "summary": "Bệnh đốm nâu có vết tròn trên lá."})
        answer = synthesize_answer(
            "Đạo ôn có triệu chứng gì?", PASSAGES, "farm_qa", llm=wrong
        )
        self.assertEqual(answer.reason, "conflicting_output")
        model = Model()
        answer = synthesize_answer(
            "Phân biệt đạo ôn và đốm nâu?", PASSAGES, "farm_qa", llm=model
        )
        self.assertEqual(answer.reason, "insufficient_evidence")
        self.assertFalse(model.calls)

    def test_diagnosis_does_not_drop_source_negation(self):
        passages = [
            {**PASSAGES[0], "content": "Triệu chứng: Không có vết tròn trên lá."}
        ]
        context = {
            "disease_code": "Rice___Blast",
            "disease_name": "Bệnh đạo ôn trên lúa",
        }
        draft = {**Model().draft, "summary": "Bệnh đạo ôn có vết tròn trên lá."}
        answer = synthesize_answer(
            "Giải thích bệnh.",
            passages,
            "farm_diagnosis",
            context=context,
            llm=Model(draft),
        )
        self.assertEqual(answer.reason, "conflicting_output")

    def test_healthy_question_cannot_inherit_symptoms_from_another_document(self):
        passages = [
            {
                "content": "Không thấy triệu chứng bệnh thuộc tập ảnh.",
                "document_title": "Lá lúa không thấy triệu chứng bệnh",
                "source_ids": ["src_1"],
            },
            {
                "content": "Lá vàng, cây lùn, đẻ nhánh giảm.",
                "document_title": "Bệnh tungro trên lúa",
                "source_ids": ["src_1"],
            },
        ]
        model = Model(
            {
                **Model().draft,
                "summary": "Lá lúa không thấy triệu chứng bệnh, lá vàng và cây lùn.",
            }
        )
        answer = synthesize_answer(
            "Lá lúa không thấy triệu chứng bệnh được mô tả như thế nào?",
            passages,
            "farm_qa",
            llm=model,
        )
        self.assertEqual(answer.reason, "conflicting_output")
        supplied = json.loads(model.calls[0][1][1])["evidence"]
        self.assertEqual(len(supplied), 1)
        self.assertNotIn("Lá vàng", supplied[0]["content"])

    def test_wire_schema_uses_runtime_validation_without_summary_regex(self):
        with patch("app.rag.rag_service.ChatOllama", return_value=Model()) as factory:
            answer = synthesize_answer("Đạo ôn là gì?", PASSAGES, "farm_qa")
        self.assertEqual(answer.mode, "generated")
        wire = factory.call_args.kwargs["format"]
        self.assertNotIn("pattern", wire["properties"]["summary"])
        self.assertEqual(wire["properties"]["source_ids"]["maxItems"], 1)
        self.assertEqual(factory.call_args.kwargs["client_kwargs"]["timeout"], 20)

    def test_complete_json_without_period_is_accepted_but_token_cutoff_is_not(self):
        context = {
            "disease_code": "Rice___Blast",
            "disease_name": "Bệnh đạo ôn trên lúa",
        }
        passages = [
            {**PASSAGES[0], "content": "Triệu chứng: Vết bệnh thường hình thoi."}
        ]
        draft = {**Model().draft, "summary": "Vết bệnh thường hình thoi"}
        for stop, expected in [("stop", "generated"), ("length", "extractive")]:
            with self.subTest(stop=stop):
                model = Model(draft)
                response = SimpleNamespace(
                    content=json.dumps(draft, ensure_ascii=False),
                    response_metadata={"done_reason": stop},
                )
                with patch.object(model, "invoke", return_value=response):
                    answer = synthesize_answer(
                        "Giải thích bệnh.",
                        passages,
                        "farm_diagnosis",
                        context=context,
                        llm=model,
                    )
                self.assertEqual(answer.mode, expected)
                if stop == "stop":
                    self.assertEqual(answer.summary, draft["summary"])

    def test_lab_library_question_does_not_receive_room_rules(self):
        passages = [
            {
                "content": "Không tự ý cài đặt phần mềm.",
                "document_title": "Nội quy sử dụng máy tính và tài sản tại Thư viện",
                "source_ids": ["src_1"],
            },
            {
                "content": "Chỉ vào phòng máy theo lịch được duyệt.",
                "document_title": "Quy chế sử dụng phòng máy",
                "source_ids": ["src_1"],
            },
        ]
        original = copy.deepcopy(passages)
        model = Model({**Model().draft, "summary": "Không tự ý cài đặt phần mềm."})
        synthesize_answer(
            "Nội quy sử dụng máy tính và tài sản tại Thư viện quy định những gì?",
            passages,
            "lab_qa",
            llm=model,
        )
        evidence = json.loads(model.calls[0][1][1])["evidence"]
        self.assertEqual(len(evidence), 1)
        self.assertIn("Thư viện", evidence[0]["document_title"])
        self.assertEqual(passages, original)

    def test_lab_unregistered_sources_cannot_generate_a_policy_answer(self):
        passages = [{**PASSAGES[0], "source_ids": ["retired_source"]}]
        model = Model()
        answer = synthesize_answer(
            "Nội quy thư viện là gì?", passages, "lab_qa", llm=model
        )
        self.assertEqual(answer.reason, "insufficient_evidence")
        self.assertFalse(model.calls)
        self.assertEqual(answer.summary, "")

    def test_lab_title_keywords_cannot_displace_the_top_retrieved_document(self):
        passages = [
            {
                "content": "Nếu quên mật khẩu BitLocker thì không có cách nào mở được dữ liệu.",
                "document_title": "An toàn thông tin trên máy tính cá nhân",
                "source_ids": ["src_1"],
            },
            {
                "content": "Chọn tài khoản email rồi chọn THAY ĐỔI MẬT KHẨU.",
                "document_title": "Đăng nhập và đổi mật khẩu email của trường",
                "source_ids": ["src_1"],
            },
        ]
        original = copy.deepcopy(passages)
        model = Model(
            {
                **Model().draft,
                "summary": "Tài liệu cảnh báo về việc quên mật khẩu BitLocker.",
            }
        )
        synthesize_answer(
            "Nếu quên mật khẩu BitLocker thì có thể mở lại dữ liệu bằng cách nào?",
            passages,
            "lab_qa",
            llm=model,
        )
        evidence = json.loads(model.calls[0][1][1])["evidence"]
        self.assertEqual(
            [p["document_title"] for p in evidence], [passages[0]["document_title"]]
        )
        self.assertEqual(passages, original)

    def test_lab_cannot_turn_backup_advice_into_password_recovery(self):
        passages = [
            {
                "content": "Nếu quên mật khẩu BitLocker thì không có cách nào mở được dữ liệu, nên kết hợp đồng bộ dữ liệu qua OneDrive hoặc Google Drive.",
                "document_title": "An toàn thông tin trên máy tính cá nhân",
                "source_ids": ["src_1"],
            }
        ]
        for summary, mode in [
            (
                "Nếu quên mật khẩu BitLocker, có thể mở lại dữ liệu bằng cách đồng bộ dữ liệu qua OneDrive.",
                "extractive",
            ),
            (
                "Theo tài liệu, nếu quên mật khẩu BitLocker thì không có cách nào mở được dữ liệu; nên chủ động sao lưu trước.",
                "generated",
            ),
        ]:
            with self.subTest(summary=summary):
                answer = synthesize_answer(
                    "Nếu quên mật khẩu BitLocker thì có thể mở lại dữ liệu bằng cách nào?",
                    passages,
                    "lab_qa",
                    llm=Model({**Model().draft, "summary": summary}),
                )
                self.assertEqual(answer.mode, mode)
                if mode == "extractive":
                    self.assertEqual(answer.reason, "conflicting_output")
                    self.assertEqual(answer.summary, "")

    def test_lab_preserves_the_prohibition_on_abrupt_power_cutoff(self):
        passages = [
            {
                "content": "Chờ 2 đến 5 phút đến khi quạt máy chiếu dừng hẳn rồi mới ngắt nguồn điện. Khi tắt máy tuyệt đối không ngắt nguồn điện đột ngột bằng cách dập cầu dao hoặc rút phích điện.",
                "document_title": "Sử dụng máy chiếu trong phòng học",
                "source_ids": ["src_1"],
            }
        ]
        for summary, mode in [
            ("Chờ 2 đến 5 phút rồi mới ngắt nguồn điện đột ngột.", "extractive"),
            ("Chờ 2 đến 5 phút và quạt dừng hẳn rồi mới ngắt nguồn điện.", "generated"),
            ("Không ngắt nguồn điện đột ngột khi tắt máy chiếu.", "generated"),
        ]:
            with self.subTest(summary=summary):
                answer = synthesize_answer(
                    "Sau khi tắt máy chiếu cần chờ bao lâu trước khi ngắt nguồn điện?",
                    passages,
                    "lab_qa",
                    llm=Model({**Model().draft, "summary": summary}),
                )
                self.assertEqual(answer.mode, mode)
                if mode == "extractive":
                    self.assertEqual(answer.reason, "conflicting_output")

    def test_padded_numbers_keep_the_same_value_without_allowing_new_quantities(self):
        passages = [
            {
                "content": "Sinh viên được mượn 05 đơn vị tài liệu trong 15 ngày.",
                "document_title": "Mượn tài liệu tại Thư viện",
                "source_ids": ["src_1"],
            }
        ]
        for units, mode in [
            ("5", "generated"),
            ("05", "generated"),
            ("6", "extractive"),
        ]:
            with self.subTest(units=units):
                answer = synthesize_answer(
                    "Sinh viên được mượn bao nhiêu tài liệu?",
                    passages,
                    "lab_qa",
                    llm=Model(
                        {
                            **Model().draft,
                            "summary": f"Sinh viên được mượn {units} đơn vị tài liệu trong 15 ngày.",
                        }
                    ),
                )
                self.assertEqual(answer.mode, mode)

    def test_lab_student_answer_cannot_use_staff_login_instructions(self):
        passages = [
            {
                "content": "Với cán bộ: nhập tài khoản egov.\nVới sinh viên: bỏ qua đăng nhập và chọn đường link miễn phí.",
                "document_title": "Kết nối Wi-Fi",
                "source_ids": ["src_1"],
            }
        ]
        for summary, mode in [
            ("Sinh viên nhập tài khoản egov.", "extractive"),
            ("Sinh viên bỏ qua đăng nhập và chọn đường link miễn phí.", "generated"),
        ]:
            with self.subTest(summary=summary):
                model = Model({**Model().draft, "summary": summary})
                answer = synthesize_answer(
                    "Sinh viên cần làm gì khi kết nối Wi-Fi?",
                    passages,
                    "lab_qa",
                    llm=model,
                )
                self.assertEqual(answer.mode, mode)
                supplied = json.loads(model.calls[0][1][1])["evidence"][0]["content"]
                self.assertNotIn("egov", supplied)

    def test_valid_diagnosis_paraphrase_is_not_required_to_copy_source_words(self):
        passages = [
            {
                **PASSAGES[0],
                "content": "Triệu chứng: Vết trên lá thường thuôn/hình thoi, tâm trắng-xám, rìa nâu đỏ.",
            }
        ]
        draft = {
            **Model().draft,
            "summary": "Tổn thương trên phiến lá thường mang dạng thoi, phần giữa xám nhạt và viền nâu đỏ.",
        }
        answer = synthesize_answer(
            "Giải thích bệnh.",
            passages,
            "farm_diagnosis",
            context={
                "disease_code": "Rice___Blast",
                "disease_name": "Bệnh đạo ôn trên lúa",
            },
            llm=Model(draft),
        )
        self.assertEqual(answer.mode, "generated")

    def test_healthy_scope_limitation_is_not_a_refusal(self):
        passages = [
            {
                "content": "Triệu chứng: Không thấy triệu chứng thuộc năm lớp bệnh trong tập ảnh; vẫn có thể tồn tại vấn đề khác ngoài phạm vi model.",
                "document_title": "Lá lúa không thấy triệu chứng bệnh",
                "source_ids": ["src_1"],
            }
        ]
        draft = {
            **Model().draft,
            "summary": "Kết quả chưa ghi nhận năm bệnh được phân loại; vấn đề khác ngoài phạm vi mô hình vẫn cần theo dõi.",
        }
        answer = synthesize_answer(
            "Giải thích nhãn khỏe.",
            passages,
            "farm_diagnosis",
            context={
                "disease_code": "Rice___healthy",
                "disease_name": "Lá lúa không thấy triệu chứng bệnh",
            },
            llm=Model(draft),
        )
        self.assertEqual(answer.mode, "generated")

    def test_farm_qa_allows_grounded_synonyms(self):
        passages = [
            {
                **PASSAGES[0],
                "content": "Loại bệnh: Nấm.\nTriệu chứng: Vết trên lá thường hình thoi, tâm trắng-xám.",
            }
        ]
        draft = {
            **Model().draft,
            "summary": "Đạo ôn do tác nhân nấm gây nên; tổn thương dạng thoi thường có vùng giữa nhạt màu.",
        }
        answer = synthesize_answer(
            "Đạo ôn có triệu chứng và tác nhân gì?",
            passages,
            "farm_qa",
            llm=Model(draft),
        )
        self.assertEqual(answer.mode, "generated")

    def test_lab_negated_login_paraphrase_is_not_a_staff_instruction(self):
        passages = [
            {
                "content": "Với cán bộ: nhập tài khoản egov.\nVới sinh viên: bỏ qua đăng nhập và chọn đường link miễn phí.",
                "document_title": "Kết nối Wi-Fi",
                "source_ids": ["src_1"],
            }
        ]
        draft = {
            **Model().draft,
            "summary": "Sinh viên không cần nhập tài khoản; hãy mở liên kết truy cập miễn phí.",
        }
        answer = synthesize_answer(
            "Sinh viên cần làm gì khi kết nối Wi-Fi?",
            passages,
            "lab_qa",
            llm=Model(draft),
        )
        self.assertEqual(answer.mode, "generated")

    def test_missing_period_does_not_hide_a_complete_json_answer(self):
        draft = {**Model().draft, "summary": "Theo dõi và giữ ruộng thông thoáng"}
        answer = synthesize_answer(
            "Cách phòng ngừa đạo ôn?", PASSAGES, "farm_qa", llm=Model(draft)
        )
        self.assertEqual(answer.mode, "generated")

    def test_token_limit_is_a_generation_failure_even_with_parseable_json(self):
        model = Model()
        response = SimpleNamespace(
            content=json.dumps(model.draft, ensure_ascii=False),
            response_metadata={"done_reason": "length"},
        )
        with patch.object(model, "invoke", return_value=response):
            answer = synthesize_answer("Đạo ôn là gì?", PASSAGES, "farm_qa", llm=model)
        self.assertNotEqual(answer.mode, "generated")

    def test_bad_optional_format_does_not_hide_a_valid_summary(self):
        draft = {**Model().draft, "steps": ["查找信息"], "note": "**Ghi chú**"}
        answer = synthesize_answer(
            "Đạo ôn được mô tả như thế nào?", PASSAGES, "farm_qa", llm=Model(draft)
        )
        self.assertEqual(answer.mode, "generated")
        self.assertEqual(answer.steps, [])
        self.assertEqual(answer.note, "Ghi chú")
        self.assertEqual(answer.summary, draft["summary"])

    def test_compact_json_and_repeated_optional_content_keep_valid_answer(self):
        for draft in [
            {"summary": "Theo dõi bệnh đạo ôn", "source_ids": ["src_1"]},
            {**Model().draft, "steps": [Model().draft["summary"]]},
        ]:
            with self.subTest(draft=draft):
                answer = synthesize_answer(
                    "Đạo ôn là gì?", PASSAGES, "farm_qa", llm=Model(draft)
                )
                self.assertEqual(answer.mode, "generated")
                self.assertEqual(answer.steps, [])

    def test_long_summary_is_reflowed_at_complete_sentences_without_losing_text(self):
        from app.rag.answer_contract import AnswerDraft

        sentences = [
            "Bệnh than lá do nấm gây ra và có nhiều đốm đen trên lá, cần đối chiếu tài liệu và theo dõi thêm tại ruộng."
        ] * 4
        full = " ".join(sentences)
        draft = AnswerDraft.model_validate({"summary": full, "source_ids": ["src_1"]})
        self.assertLessEqual(len(draft.summary), 350)
        self.assertLessEqual(len(draft.note), 220)
        self.assertEqual(draft.summary + " " + draft.note, full)
        self.assertEqual(draft.source_ids, ["src_1"])

    def test_bold_markers_do_not_make_supported_summary_unusable(self):
        draft = {**Model().draft, "summary": "Bệnh **đạo ôn** cần được theo dõi."}
        answer = synthesize_answer(
            "Đạo ôn là gì?", PASSAGES, "farm_qa", llm=Model(draft)
        )
        self.assertEqual(answer.mode, "generated")
        self.assertEqual(answer.summary, "Bệnh đạo ôn cần được theo dõi.")

    def test_explicit_refusal_discards_text_without_losing_reason(self):
        draft = {**Model().draft, "abstain_reason": "requires_authority"}
        answer = synthesize_answer(
            "Ai được phê duyệt?", PASSAGES, "lab_qa", llm=Model(draft)
        )
        self.assertEqual(answer.reason, "requires_authority")
        self.assertEqual(answer.summary, "")

    def test_lab_approval_request_requires_staff_but_policy_question_can_generate(self):
        model = Model()
        answer = synthesize_answer(
            "Bạn hãy phê duyệt đơn mượn thiết bị giúp tôi.",
            PASSAGES,
            "lab_qa",
            llm=model,
        )
        self.assertEqual(answer.reason, "requires_authority")
        self.assertFalse(model.calls)
        policy = synthesize_answer(
            "Cho tôi biết ai có quyền phê duyệt?", PASSAGES, "lab_qa", llm=Model()
        )
        self.assertEqual(policy.mode, "generated")

    def test_lab_cannot_claim_it_will_execute_a_workflow(self):
        draft = {
            **Model().draft,
            "summary": "Tôi sẽ tiếp nhận đơn yêu cầu và lập phiếu giao việc.",
        }
        answer = synthesize_answer(
            "Quy trình xử lý đơn như thế nào?", PASSAGES, "lab_qa", llm=Model(draft)
        )
        self.assertEqual(answer.reason, "requires_authority")
        self.assertEqual(answer.summary, "")

    def test_timeout_is_not_a_successful_generation(self):
        model = Model(error=TimeoutError())
        answer = synthesize_answer("Đạo ôn là gì?", PASSAGES, "farm_qa", llm=model)
        self.assertEqual(answer.mode, "extractive")
        self.assertEqual(len(model.calls), 1)

    def test_malformed_json_falls_back(self):
        incomplete = {**Model().draft, "steps": ["Giữ đồng ruộng"]}
        for raw in ["{", "{}", json.dumps(incomplete, ensure_ascii=False)[:-1]]:
            model = Model()
            with (
                self.subTest(raw=raw),
                patch.object(
                    model, "invoke", return_value=SimpleNamespace(content=raw)
                ),
            ):
                answer = synthesize_answer(
                    "Cách phòng ngừa bệnh?", PASSAGES, "farm_qa", llm=model
                )
                self.assertEqual(answer.mode, "extractive")

    def test_farm_prompt_does_not_receive_drug_lines(self):
        passages = [
            {
                **PASSAGES[0],
                "content": "Triệu chứng: Lá có vết bệnh.\nThuốc: Newtec 300SC, liều 2 ml/l.",
            }
        ]
        model = Model()
        synthesize_answer(
            "Giải thích bệnh.",
            passages,
            "farm_diagnosis",
            context={
                "disease_code": "Rice___Blast",
                "disease_name": "Bệnh đạo ôn trên lúa",
            },
            llm=model,
        )
        user_message = json.loads(model.calls[0][1][1])
        self.assertNotIn("Newtec", user_message["evidence"][0]["content"])
        self.assertIn("Lá có vết bệnh", user_message["evidence"][0]["content"])

    def test_profiles_are_explicit_and_do_not_share_context(self):
        prompts = [
            build_answer_messages("Bệnh là gì?", PASSAGES, profile, {})
            for profile in ["farm_qa", "farm_diagnosis", "lab_qa"]
        ]
        self.assertIn("Do NOT diagnose", prompts[0][0][1])
        self.assertIn("NO IMAGE", prompts[1][0][1])
        self.assertIn("university regulations", prompts[2][0][1])
        self.assertEqual(json.loads(prompts[0][1][1])["context"], {})

    def test_diagnosis_prompt_has_no_overwritten_general_template(self):
        system = build_answer_messages(
            "Bệnh là gì?", PASSAGES, "farm_diagnosis", {}
        )[0][1]
        self.assertIn("Chỉ diễn giải MỘT ý", system)
        self.assertNotIn("Do NOT diagnose", system)
        self.assertNotIn("Use ONLY evidence/context", system)

    def test_request_rejects_untrusted_profile_and_excess_context(self):
        from pydantic import ValidationError

        base = {"question": "Quy định gì?", "namespace": "lab", "passages": []}
        for change in [
            {"profile": "farm_diagnosis"},
            {"namespace": "other"},
            {"model": "other"},
            {"question": " "},
            {"passages": [PASSAGES[0]] * 5},
        ]:
            with self.subTest(change=change), self.assertRaises(ValidationError):
                KnowledgeAnswerRequest.model_validate({**base, **change})

    def test_endpoint_requires_service_key_and_preserves_namespace(self):
        from fastapi import FastAPI
        from fastapi.testclient import TestClient

        from app.api.router import inference_router
        from app.core.config import Settings, get_settings
        from app.rag.rag_service import fallback_answer

        app = FastAPI()
        app.include_router(inference_router, prefix="/api/v1")
        app.dependency_overrides[get_settings] = lambda: Settings(
            ai_service_api_key="unit-key", _env_file=None
        )
        client = TestClient(app)
        body = {"question": "Quy định gì?", "namespace": "lab", "passages": []}
        self.assertEqual(
            client.post("/api/v1/knowledge/answer", json=body).status_code, 401
        )
        with patch(
            "app.api.router.synthesize_answer", return_value=fallback_answer([])
        ) as model:
            response = client.post(
                "/api/v1/knowledge/answer",
                json=body,
                headers={"X-Service-Api-Key": "unit-key"},
            )
            self.assertEqual(response.status_code, 200)
            self.assertEqual(model.call_args.args[2], "lab_qa")
            self.assertEqual(response.json()["mode"], "insufficient_context")

    def test_diagnosis_opt_in_keeps_rule_facts_and_releases_read_transaction(self):
        from unittest.mock import Mock

        from sqlalchemy.orm import Session

        from app.api.schemas import DiagnoseRequest
        from app.rag.answer_contract import GeneratedAnswer
        from app.services.inference_service import run_stateless_diagnose

        cv = {
            "disease_code": "Rice___Blast",
            "disease_name": "Đạo ôn",
            "confidence": 0.91,
            "model_name": "test-model",
            "model_version": "test",
        }
        rule = {
            "rule_id": "1",
            "pesticide": "backend-fact",
            "dose_min": 0.5,
            "dose_max": 0.6,
            "dose_unit": "L/ha",
            "phi_days": 14,
            "version": "1",
        }
        state = {
            "cv_result": cv,
            "rag_context": [{**PASSAGES[0], "similarity": 0.8}],
            "treatment_rule": {"path": "treatment", "rules": [rule]},
            "recommendation": {"selected_rule_id": "1"},
        }
        payload = {
            "request_id": "test",
            "crop_season_id": 1,
            "image_url": "http://backend/image",
            "field_context": {"parcel_code": "PRIVATE-PARCEL"},
        }
        for opted in [False, True]:
            db = Mock(spec=Session)
            db.in_transaction.return_value = True
            explanation = GeneratedAnswer(
                mode="generated",
                **Model().draft,
                model_name="qwen2.5:1.5b",
                prompt_version="farm_diagnosis-v1",
            )

            def generate(*args, **kwargs):
                db.rollback.assert_called_once()
                self.assertEqual(args[2], "farm_diagnosis")
                self.assertEqual(kwargs["context"]["disease_code"], "Rice___Blast")
                self.assertNotIn("PRIVATE-PARCEL", str(kwargs))
                self.assertNotIn("backend-fact", str(args))
                return explanation

            with (
                patch(
                    "app.services.inference_service.farm_graph.invoke",
                    return_value=state,
                ),
                patch(
                    "app.rag.rag_service.synthesize_answer", side_effect=generate
                ) as model,
            ):
                response = run_stateless_diagnose(
                    payload=DiagnoseRequest.model_validate(
                        {**payload, "include_explanation": opted}
                    ),
                    image_bytes=b"image",
                    predictor=SimpleNamespace(predict=lambda _: cv),
                    db=db,
                    backend_client=object(),
                ).model_dump(mode="json")
                self.assertEqual(response["structured_context"]["rules"], [rule])
                self.assertEqual(response["recommendation"]["selected_rule_id"], "1")
                self.assertEqual("generated_explanation" in response, opted)
                self.assertEqual(model.call_count, int(opted))


if __name__ == "__main__":
    unittest.main()


class OverviewAnswerChecks(unittest.TestCase):
    def test_lab_overview_can_use_multiple_registered_sources(self):
        passages = [
            dict(
                document_title="Wifi",
                content="Với sinh viên: Nhà trường hỗ trợ kết nối mạng.\nVới cán bộ: Dùng quy trình nội bộ riêng.",
                source_ids=["src_wifi"],
            ),
            dict(
                document_title="Email",
                content="Nhà trường cung cấp hộp thư cho sinh viên.",
                source_ids=["src_email"],
            ),
        ]
        model = Model(
            dict(
                summary="Có dịch vụ mạng và hộp thư cho sinh viên.",
                steps=[],
                note=None,
                source_ids=["src_wifi", "src_email"],
            )
        )
        with patch(
            "app.rag.rag_service.load_source_registry",
            return_value={"src_wifi": {}, "src_email": {}},
        ):
            answer = synthesize_answer(
                "Có những dịch vụ hỗ trợ nào cho sinh viên?",
                passages,
                "lab_qa",
                llm=model,
            )
        self.assertEqual(answer.mode, "generated")
        supplied = json.loads(model.calls[0][1][1])["evidence"]
        self.assertEqual(len(supplied), 2)
        self.assertNotIn("quy trình nội bộ", str(supplied))
        self.assertEqual(set(answer.source_ids), {"src_wifi", "src_email"})
