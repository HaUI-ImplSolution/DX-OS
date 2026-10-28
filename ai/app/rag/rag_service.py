# SPDX-License-Identifier: Apache-2.0
import logging
import re
import unicodedata
from typing import Any

from httpx import HTTPError
from langchain_ollama import ChatOllama
from ollama import ResponseError
from pydantic import ValidationError
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.rag.answer_contract import AnswerDraft, AnswerReason, GeneratedAnswer, Profile
from app.rag.prompt_builder import build_answer_messages, build_rag_prompt
from app.rag.retriever import RAGRetriever
from app.rag.scope_gate import ANSWERABLE, IN_SCOPE_NO_EVIDENCE, classify_scope
from app.rag.source_registry import REGISTRY_FILE, load_source_registry

logger = logging.getLogger(__name__)


def build_citations(contexts: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Resolve unique source IDs in retrieval order to auditable citations."""

    registry = load_source_registry()
    citations: list[dict[str, Any]] = []
    seen: set[str] = set()

    for item in contexts:
        for source_id in item.get("source_ids", []):
            if source_id in seen:
                continue
            seen.add(source_id)

            source = registry[source_id]
            citations.append(
                {
                    "source_id": source_id,
                    "title": source["title"],
                    "organization": source["organization"],
                    "url": source["url"],
                    "authority_level": source["authority_level"],
                    "accessed_at": source["accessed_at"],
                    "document_id": item["document_id"],
                    "chunk_id": item["chunk_id"],
                    "similarity": item["similarity"],
                }
            )

    return citations


class RAGService:
    def __init__(
        self,
        model_name: str | None = None,
        base_url: str | None = None,
    ):
        settings = get_settings()
        self.retriever = RAGRetriever()
        self.llm = ChatOllama(
            model=model_name or settings.ollama_model,
            base_url=base_url or settings.ollama_base_url,
            temperature=0,
            client_kwargs={"timeout": 30},
        )

    def answer(
        self,
        db: Session,
        question: str,
        top_k: int = 3,
        disease_code: str | None = None,
        namespace: str = "farm",
        document_type: str | None = None,
    ) -> dict:
        decision = classify_scope(question, namespace)
        base = {
            "question": question,
            "namespace": namespace,
            "scope": decision.scope,
            "scope_confidence": decision.confidence,
            "reason_code": decision.reason_code,
            "sources": [],
        }
        if not decision.retrieval_allowed:
            return {
                **base,
                "answer": "Xin lỗi, chatbot chỉ hỗ trợ kiến thức về lúa/nông nghiệp."
                if namespace == "farm"
                else "Xin lỗi, câu hỏi nằm ngoài phạm vi tài liệu DX-Lab.",
                "answer_mode": "out_of_scope",
            }

        contexts = self.retriever.retrieve(
            db=db,
            query=question,
            top_k=top_k,
            disease_code=disease_code,
            namespace=namespace,
            document_type=document_type,
        )
        if not contexts:
            return {
                **base,
                "scope": IN_SCOPE_NO_EVIDENCE,
                "answer": "Chưa có đủ tài liệu đã kiểm duyệt để trả lời câu hỏi này.",
                "answer_mode": "no_evidence",
            }
        prompt = build_rag_prompt(
            question=question,
            contexts=contexts,
        )
        # Conservative MVP gate: only verbatim evidence can leave the RAG layer.
        # Structured drug/dose/PHI facts are returned by the SQL service separately.
        evidence = "\n\n".join(item["content"] for item in contexts)
        fallback = evidence or "Chưa có tài liệu phù hợp để trả lời."
        answer = fallback
        if contexts:
            try:
                draft = self.llm.invoke(prompt).content
                if (
                    isinstance(draft, str)
                    and draft.strip()
                    and draft.strip() in evidence
                ):
                    answer = draft.strip()
            except (HTTPError, ResponseError, TimeoutError, ConnectionError):
                # RAG evidence remains usable when the optional LLM is unavailable.
                answer = fallback

        return {
            **base,
            "scope": ANSWERABLE,
            "answer": answer,
            "answer_mode": "grounded_excerpt",
            "sources": build_citations(contexts),
        }


MEDICATION = re.compile(
    r"thuốc|hoạt\s*chất|liều|\bphi\b|cách\s*ly|nồng\s*độ|h[oó]a\s*học|hoá\s*học|phun|"
    r"\b(?:ml|mg|kg|g|l)\s*/\s*(?:ha|l|lít|sào)|\b\w+\s*\d+(?:\.\d+)?\s*(?:sc|ec|wp|wg)\b",
    re.IGNORECASE,
)
UNSAFE_FORMAT = re.compile(
    r"[\u3400-\u9fff]|https?://|```|\*\*|__|<[/!A-Za-z]|(?:^|\n)\s*(?:[#*•-]|\d+[.)]\s)"
)
OBSERVATION = re.compile(
    r"(?:tôi|chúng tôi)\s*(?:thấy|quan sát)|(?:ảnh|hình ảnh)\s*(?:cho thấy|có vết|xuất hiện)",
    re.IGNORECASE,
)
CONFIRMED = re.compile(
    r"(?:ruộng|lúa|cây|bạn)\s*(?:của bạn\s*)?(?:đã|đang|chắc chắn)\s*(?:bị|mắc)|đã được (?:phê )?duyệt",
    re.IGNORECASE,
)


ABSTENTION = re.compile(
    r"^(?:(?:tôi|hiện tại|hiện|hệ thống|trợ lý)\s+)*"
    r"(?:(?:không|chưa)\s+(?:có\s+)?(?:đủ\s+(?:thông tin|tài liệu|căn cứ)|thể\s+(?:trả lời|kết luận))"
    r"|câu hỏi này\s+(?:nằm\s+)?ngoài\s+(?:phạm vi|thẩm quyền))",
    re.IGNORECASE,
)


def fallback_answer(
    passages: list[dict],
    *,
    medication: bool = False,
    reason: AnswerReason | None = None,
) -> GeneratedAnswer:
    """Return status only; the UI owns notices and original evidence stays separate."""
    return GeneratedAnswer(
        mode="extractive" if passages else "insufficient_context",
        summary="",
        reason=reason
        or (
            "requires_authority"
            if medication
            else "generation_failed"
            if passages
            else "insufficient_evidence"
        ),
    )


def _words(text: str) -> str:
    text = unicodedata.normalize("NFD", text.casefold().replace("đ", "d"))
    return " ".join(
        re.findall(
            r"[a-z0-9]+", "".join(c for c in text if not unicodedata.combining(c))
        )
    )


def _disease_aliases() -> dict[str, set[str]]:
    from app.cv.contract import class_contract

    return {
        entry["disease_code"]: {
            _words(entry["model_label"]),
            _words(
                entry["disease_name"]
                .removeprefix("Bệnh ")
                .split("(")[0]
                .strip()
                .removesuffix(" trên lúa")
                .removesuffix(" lúa")
            ),
            _words(entry["disease_code"]),
        }
        for entry in class_contract()["classes"]
    }


def _mentions(text: str, aliases: set[str]) -> bool:
    normalized = f" {_words(text)} "
    return any(f" {alias} " in normalized for alias in aliases)


def _asserts(text: str, pattern: str) -> bool:
    """Match an explicit claim, allowing nearby negation."""
    for clause in re.split(r"[.;!?\n]", text):
        normalized = _words(clause)
        for match in re.finditer(pattern, normalized):
            prefix = " ".join(normalized[: match.start()].split()[-7:])
            if not re.search(
                r"\b(?:khong|chua|tranh|theo doi|phan biet|khac voi)\b", prefix
            ):
                return True
    return False


def _known_conflict(text: str, passages: list[dict], target: str | None) -> bool:
    # ponytail: checks known explicit contradictions, not semantic correctness of every paraphrase.
    support = " ".join(p["content"] for p in passages)
    normalized = _words(support)
    if _asserts(
        text, r"\b(?:luon (?:co|bi|mac|gay|xuat hien)|chac chan|khoe hoan toan)\b"
    ):
        return True
    shapes = (r"\b(?:hinh|dang) thoi\b", r"\b(?:tron|bau duc)\b")
    for expected, opposite in (shapes, shapes[::-1]):
        if (
            re.search(expected, normalized)
            and not re.search(opposite, normalized)
            and _asserts(text, opposite)
        ):
            return True
    for clause in re.split(r"[.;!?\n]", support):
        negative = re.search(r"\bkhong (co|can|duoc|nen) (.+)", _words(clause))
        if negative and _asserts(
            text, r"\b" + re.escape(negative[1] + " " + negative[2]) + r"\b"
        ):
            return True
    if target == "Rice___healthy" and _asserts(
        text, r"\b(?:la vang|cay lun|dom den|co vet benh)\b"
    ):
        return True
    return False


def _qa_context(passages: list[dict], budget: int = 2000) -> list[dict]:
    """Keep complete evidence lines and reserve room for each selected document."""
    packed = []
    per_document = budget // max(1, min(len(passages), 4))
    for passage in passages[:4]:
        lines, used = [], 0
        for line in passage["content"].splitlines():
            if line.strip() and used + len(line) + 1 <= per_document:
                lines.append(line)
                used += len(line) + 1
        if lines:
            packed.append({**passage, "content": "\n".join(lines)})
    return packed


def synthesize_answer(
    question: str,
    passages: list[dict],
    profile: Profile,
    *,
    context: dict | None = None,
    llm=None,
) -> GeneratedAnswer:
    """Generate bounded wording without modifying evidence or treatment facts."""
    if profile == "lab_qa" and re.search(r"(?:phê\s+)?duyệt", question, re.IGNORECASE):
        requested = re.search(
            r"hãy|giúp tôi|hộ tôi|giùm tôi|vui lòng|tôi muốn bạn",
            question,
            re.IGNORECASE,
        )
        knowledge = re.search(
            r"\bai\b|quy trình|hướng dẫn|\bcách\b|tại sao|vì sao|thế nào",
            question,
            re.IGNORECASE,
        )
        if requested and not knowledge:
            return fallback_answer(passages, reason="requires_authority")
    if not passages:
        return fallback_answer([])
    if profile.startswith("farm_") and MEDICATION.search(question):
        return fallback_answer(passages, medication=True)
    context = dict(context or {})
    aliases = _disease_aliases() if profile.startswith("farm_") else {}
    target = context.get("disease_code")
    if profile == "farm_diagnosis":
        from app.cv.contract import class_contract

        canonical = next(
            (e for e in class_contract()["classes"] if e["disease_code"] == target),
            None,
        )
        if (
            canonical is None
            or context.get("disease_name") != canonical["disease_name"]
        ):
            return fallback_answer(passages, reason="conflicting_output")
    foreign = (
        set().union(*(names for code, names in aliases.items() if code != target))
        if target
        else set()
    )
    asked = {code for code, names in aliases.items() if _mentions(question, names)}
    if profile == "farm_qa":
        query = _words(question)
        personal = re.search(
            r"\b(?:cua (?:toi|minh|em)|(?:ruong|lua) (?:toi|minh|em)|(?:toi|minh|em) (?:thay|quan sat))\b",
            query,
        )
        diagnosis = (
            re.search(
                r"\b(?:co phai|chac chan|chan doan|xac dinh benh|dung la)\b", query
            )
            or not asked
            and re.search(r"\b(?:benh (?:gi|nao)|bi gi)\b", query)
        )
        if personal and diagnosis:
            return fallback_answer(passages, reason="insufficient_evidence")
    if (
        profile == "farm_qa"
        and not asked
        and re.search(
            r"\b(?:khong|chua) thay (?:co )?(?:dau hieu|trieu chung)\b",
            _words(question),
        )
    ):
        asked = {"Rice___healthy"}
    comparison = bool(
        re.search(r"phân biệt|so sánh|khác nhau|giống nhau", question, re.IGNORECASE)
    )
    if profile == "farm_qa" and (comparison or target and asked - {target}):
        covered = {
            code
            for code, names in aliases.items()
            if any(_mentions(p.get("document_title", ""), names) for p in passages)
        }
        if not asked <= covered or not comparison and asked - {target}:
            return fallback_answer(passages, reason="insufficient_evidence")
    focus = target or (next(iter(asked)) if len(asked) == 1 else None)
    if focus and not comparison:
        foreign = set().union(
            *(names for code, names in aliases.items() if code != focus)
        )
    registered = (
        load_source_registry(REGISTRY_FILE.parent / "lab/source_registry.json")
        if profile == "lab_qa"
        else None
    )
    safe = []
    for passage in passages:
        if (
            registered is not None
            and not set(passage.get("source_ids", [])) <= registered.keys()
        ):
            continue
        if (
            profile == "farm_qa"
            and focus
            and not comparison
            and not _mentions(
                passage.get("document_title", ""), aliases.get(focus, set())
            )
        ):
            continue
        lines = [line for line in passage["content"].splitlines() if line.strip()]
        if profile.startswith("farm_"):
            lines = [line for line in lines if not MEDICATION.search(line)]
        if (
            profile == "farm_diagnosis"
            or profile == "farm_qa"
            and focus
            and not comparison
        ):
            clauses = [
                clause.strip()
                for line in lines
                for clause in (
                    re.split(r";|(?<=[.!?])\s+", line)
                    if _mentions(line, foreign)
                    else [line]
                )
            ]
            lines = [
                clause
                for clause in clauses
                if clause and not _mentions(clause, foreign)
            ]
        if lines and passage.get("source_ids"):
            safe.append(
                {
                    "content": "\n".join(lines),
                    "document_title": passage.get("document_title", ""),
                    "source_ids": passage["source_ids"],
                }
            )
    if profile == "farm_diagnosis":
        # Keep one complete factual clause; the original retrieved evidence is returned unchanged.
        fact = next(
            (
                (p, line)
                for label in (
                    "Triệu chứng:",
                    "Đặc điểm:",
                    "Tác nhân:",
                    "Phòng ngừa:",
                    "Biện pháp canh tác:",
                )
                for p in safe
                for line in p["content"].splitlines()
                if line.startswith(label)
            ),
            None,
        )
        safe = [{**fact[0], "content": fact[1]}] if fact else []
    overview = profile != "farm_diagnosis" and bool(
        re.search(
            r"tong quan|cac dich vu|nhung dich vu|cham soc lua|quy trinh canh tac|dich vu ho tro|cac nhom",
            _words(question),
        )
    )
    if overview:
        context["multi_source"] = True
    if profile == "lab_qa" and safe and not overview:
        # Keep the retriever's document order; title keywords can select an unrelated policy.
        title = safe[0]["document_title"]
        safe = [p for p in safe if p["document_title"] == title]
    role_context = []
    if profile == "lab_qa":
        roles = [
            role
            for role in ("sinh viên", "giảng viên", "cán bộ")
            if _mentions(question, {_words(role)})
        ]
        if len(roles) == 1 and overview:
            other_roles = [
                role for role in ("sinh viên", "giảng viên", "cán bộ") if role != roles[0]
            ]
            for passage in safe:
                lines = [
                    line
                    for line in passage["content"].splitlines()
                    if not any(
                        re.match(rf"^(?:Với\s+)?{role}\s*:", line, re.IGNORECASE)
                        for role in other_roles
                    )
                ]
                if lines:
                    role_context.append({**passage, "content": "\n".join(lines)})
            safe = role_context
        elif len(roles) == 1:
            for passage in safe:
                lines = [
                    line
                    for line in passage["content"].splitlines()
                    if re.match(rf"^(?:Với\s+)?{roles[0]}\s*:", line, re.IGNORECASE)
                ]
                if lines:
                    role_context.append({**passage, "content": "\n".join(lines)})
            if role_context:
                safe = role_context
    if profile != "farm_diagnosis":
        safe = _qa_context(safe)
    allowed = set(s for p in safe for s in p["source_ids"])
    if not allowed or sum(len(p["content"]) for p in safe) > 2000:
        return fallback_answer(passages, reason="insufficient_evidence")
    settings = get_settings()
    schema = AnswerDraft.model_json_schema()
    # Grammar length limits can cut a sentence; validate lengths after generation instead.
    schema["properties"]["summary"].pop("maxLength", None)
    schema["properties"]["steps"]["items"].pop("maxLength", None)
    schema["properties"]["note"] = {"type": "null"}
    schema["properties"]["source_ids"]["items"]["enum"] = sorted(allowed)
    schema["properties"]["source_ids"]["maxItems"] = (
        min(4, len(allowed)) if overview else 1
    )
    actions_requested = bool(
        re.search(
            r"làm gì|làm sao|cách |xử lý|điều trị|hướng dẫn|các bước|cần làm|khắc phục|phòng ngừa",
            question,
            re.IGNORECASE,
        )
    )
    schema["properties"]["steps"]["maxItems"] = 2 if actions_requested else 0
    try:
        if llm is None:
            llm = ChatOllama(
                model=settings.ollama_model,
                base_url=settings.ollama_base_url,
                temperature=0,
                format=schema,
                num_predict=192,
                num_thread=4,
                num_ctx=2048,
                client_kwargs={"timeout": 20},
            )
        response = llm.invoke(
            build_answer_messages(question, safe, profile, context), stream=False
        )
        draft = AnswerDraft.model_validate_json(response.content)
        if draft.abstain_reason:
            return fallback_answer(passages, reason=draft.abstain_reason)
        if getattr(response, "response_metadata", {}).get("done_reason") == "length":
            logger.warning(
                "Qwen answer rejected: profile=%s stage=token_limit", profile
            )
            return fallback_answer(passages)
        draft.steps = [
            step
            for step in draft.steps
            if actions_requested and not UNSAFE_FORMAT.search(step)
        ]
        if draft.note and UNSAFE_FORMAT.search(draft.note):
            draft.note = None
        text = "\n".join([draft.summary, *draft.steps, draft.note or ""])
        if ABSTENTION.search(draft.summary):
            return fallback_answer(passages, reason="insufficient_evidence")
        if profile == "lab_qa" and re.search(
            r"\b(?:tôi|chúng tôi)\s+(?:đã|sẽ|đang)\s+(?:(?:phê\s+)?duyệt|tiếp nhận|tạo|lập|gửi|đặt|hủy|cập nhật|hoàn tất)\b",
            text,
            re.IGNORECASE,
        ):
            return fallback_answer(passages, reason="requires_authority")
        if profile == "lab_qa":
            # Known policy contradictions only; this is not a general semantic verifier.
            support = _words(" ".join(p["content"] for p in safe))
            if (
                "quen mat khau" in _words(question)
                and re.search(
                    r"\bkhong co cach nao (?:mo|khoi phuc) duoc du lieu\b", support
                )
                and _asserts(
                    text, r"\b(?:mo|khoi phuc|truy cap) (?:(?:lai|duoc) )*du lieu\b"
                )
            ) or (
                "khong ngat nguon dien dot ngot" in support
                and _asserts(text, r"\bngat nguon dien dot ngot\b")
            ):
                return fallback_answer(passages, reason="conflicting_output")
        if role_context:
            support = _words(" ".join(p["content"] for p in role_context))
            if re.search(r"bo qua (?:viec )?dang nhap", support) and _asserts(
                text, r"\b(?:nhap tai khoan|can dang nhap|dang nhap tai khoan)\b"
            ):
                return fallback_answer(passages, reason="conflicting_output")
        if profile == "farm_qa" and not comparison and (target or len(asked) == 1):
            expected = {target} if target else asked
            unrelated = set().union(
                *(names for code, names in aliases.items() if code not in expected)
            )
            if _mentions(text, unrelated):
                return fallback_answer(passages, reason="conflicting_output")
        if profile == "farm_diagnosis" and _mentions(text, foreign):
            return fallback_answer(passages, reason="conflicting_output")
        if profile.startswith("farm_") and _known_conflict(text, safe, target or focus):
            return fallback_answer(passages, reason="conflicting_output")
        evidence = "\n".join(p["content"] for p in safe) + str(context)
        numbers = {
            value.lstrip("0") or "0" for value in re.findall(r"\d+(?:[.,]\d+)?", text)
        }
        supported_numbers = {
            value.lstrip("0") or "0"
            for value in re.findall(r"\d+(?:[.,]\d+)?", evidence)
        }
        if (
            not set(draft.source_ids) <= allowed
            or (draft.steps and not actions_requested)
            or UNSAFE_FORMAT.search(text)
            or not numbers <= supported_numbers
            or (
                profile.startswith("farm_")
                and (
                    MEDICATION.search(text)
                    or OBSERVATION.search(text)
                    or CONFIRMED.search(text)
                )
            )
        ):
            return fallback_answer(passages)
        return GeneratedAnswer(
            mode="generated",
            **draft.model_dump(exclude={"abstain_reason"}),
            model_name=settings.ollama_model,
            prompt_version=f"{profile}-v2",
        )
    except ValidationError as exc:
        logger.warning(
            "Qwen answer rejected: profile=%s stage=contract types=%s",
            profile,
            ",".join(sorted({error["type"] for error in exc.errors()})),
        )
        return fallback_answer(passages)
    except (HTTPError, ResponseError, TimeoutError, ConnectionError) as exc:
        logger.warning(
            "Qwen answer unavailable: profile=%s error=%s", profile, type(exc).__name__
        )
        return fallback_answer(passages)
