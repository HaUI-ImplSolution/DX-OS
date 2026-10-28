# SPDX-License-Identifier: Apache-2.0
"""Legacy scope gate used only by RAGService.answer.

The chatbot gate is chat_search.outside_scope.

Small, deterministic scope gate for namespace-aware RAG questions.

The gate deliberately runs without an LLM or a database.  It is a first
boundary only: retrieval still decides whether an in-scope question has
enough evidence to answer.
"""

from dataclasses import dataclass
import re
import unicodedata


OUT_OF_SCOPE = "OUT_OF_SCOPE"
IN_SCOPE = "IN_SCOPE"
IN_SCOPE_NO_EVIDENCE = "IN_SCOPE_NO_EVIDENCE"
ANSWERABLE = "ANSWERABLE"

_FARM_TERMS = (
    "lua", "lúa", "ruong", "ruộng", "nong nghiep", "nông nghiệp",
    "hat giong", "hạt giống", "dat", "đất", "tuoi", "tưới", "sau thu hoach",
    "sau thu hoạch", "sau benh", "sâu bệnh", "benh", "bệnh",
    "co dai", "cỏ dại", "phan bon", "phân bón", "thu hoach", "thu hoạch",
)
_LAB_TERMS = (
    "haui", "wifi", "wi-fi", "email", "may chieu", "máy chiếu", "mat khau",
    "mật khẩu", "tai khoan", "tài khoản", "tin hoc", "tin học", "cntt",
    "it", "phan mem", "phần mềm", "thiet bi", "thiết bị", "quy dinh",
    "quy định", "dich vu", "dịch vụ", "sict", "faronics", "so tay",
    "sổ tay", "an toan thong tin", "an toàn thông tin", "mang khong day",
    "mạng không dây",
)
_EXPLICIT_NON_FARM = (
    "o to", "ô tô", "xe hoi", "xe hơi", "chung khoan", "chứng khoán",
    "bong da", "bóng đá", "du lich", "du lịch", "crypto", "bitcoin",
)
_EXPLICIT_NON_LAB = ("o to", "ô tô", "xe hoi", "xe hơi", "nau an", "nấu ăn")


def _fold(text: str) -> str:
    normalized = unicodedata.normalize("NFD", text.lower())
    return "".join(ch for ch in normalized if unicodedata.category(ch) != "Mn")


def _contains(text: str, terms: tuple[str, ...]) -> bool:
    return any(
        re.search(rf"(?<!\w){re.escape(term)}(?!\w)", text) is not None
        for term in terms
    )


@dataclass(frozen=True)
class ScopeDecision:
    namespace: str
    scope: str
    confidence: float
    reason_code: str
    retrieval_allowed: bool


def classify_scope(question: str, namespace: str = "farm") -> ScopeDecision:
    """Classify a question before embedding/retrieval.

    Unknown namespaces fail closed.  For known namespaces, explicit unrelated
    topics are rejected; otherwise a matching domain term permits retrieval.
    """
    folded = _fold(question.strip())
    normalized_namespace = namespace.strip().lower()

    if normalized_namespace == "farm":
        if _contains(folded, tuple(_fold(term) for term in _EXPLICIT_NON_FARM)):
            return ScopeDecision(namespace, OUT_OF_SCOPE, 0.99, "EXPLICIT_UNRELATED_TOPIC", False)
        terms = tuple(_fold(term) for term in _FARM_TERMS)
        if _contains(folded, terms):
            return ScopeDecision(namespace, IN_SCOPE, 0.90, "FARM_DOMAIN_TERM", True)
        return ScopeDecision(namespace, OUT_OF_SCOPE, 0.80, "NO_FARM_DOMAIN_TERM", False)

    if normalized_namespace == "lab":
        if _contains(folded, tuple(_fold(term) for term in _EXPLICIT_NON_LAB)):
            return ScopeDecision(namespace, OUT_OF_SCOPE, 0.99, "EXPLICIT_UNRELATED_TOPIC", False)
        terms = tuple(_fold(term) for term in _LAB_TERMS)
        if _contains(folded, terms):
            return ScopeDecision(namespace, IN_SCOPE, 0.90, "LAB_DOMAIN_TERM", True)
        return ScopeDecision(namespace, OUT_OF_SCOPE, 0.80, "NO_LAB_DOMAIN_TERM", False)

    return ScopeDecision(namespace, OUT_OF_SCOPE, 1.0, "UNKNOWN_NAMESPACE", False)
