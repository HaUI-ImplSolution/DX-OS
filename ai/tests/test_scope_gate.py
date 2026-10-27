# SPDX-License-Identifier: Apache-2.0
import unittest

from app.rag.scope_gate import IN_SCOPE, OUT_OF_SCOPE, classify_scope


class ScopeGateTest(unittest.TestCase):
    def test_farm_rejects_unrelated_question_without_retrieval_permission(self):
        decision = classify_scope("Xe ô tô nên thay dầu gì?", "farm")
        self.assertEqual(decision.scope, OUT_OF_SCOPE)
        self.assertEqual(decision.reason_code, "EXPLICIT_UNRELATED_TOPIC")
        self.assertFalse(decision.retrieval_allowed)


    def test_farm_accepts_non_disease_rice_question(self):
        decision = classify_scope("Lúa giai đoạn đẻ nhánh quản lý nước thế nào?", "farm")
        self.assertEqual(decision.scope, IN_SCOPE)
        self.assertTrue(decision.retrieval_allowed)


    def test_lab_does_not_accept_farm_question(self):
        decision = classify_scope("Lúa bị bệnh đạo ôn xử lý thế nào?", "lab")
        self.assertEqual(decision.scope, OUT_OF_SCOPE)
        self.assertFalse(decision.retrieval_allowed)


    def test_accents_and_unknown_namespace_fail_closed(self):
        self.assertTrue(classify_scope("lúa canh tac", "farm").retrieval_allowed)
        self.assertFalse(classify_scope("WiFi HaUI", "unknown").retrieval_allowed)
