"""Crawl a small allowlisted Vietnamese rice-knowledge batch into staging.

This script deliberately does not touch PostgreSQL or the production corpus.
It stores raw HTML for audit and produces processed JSON with provenance. A
human reviewer must approve the records before they are mapped to the RAG
ingestion contract.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import time
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse
from urllib.robotparser import RobotFileParser

import requests
from lxml import html as lxml_html


ROOT = Path(__file__).resolve().parents[2]
STAGING = ROOT / "data" / "rag" / "staging" / "farm"
RAW = STAGING / "raw"
PROCESSED = STAGING / "processed"

SOURCES = [
    {
        "source_id": "SRC_KN_RICE_CLIMATE_19488",
        "title": "Tài liệu tập huấn: Canh tác lúa thông minh thích ứng với biến đổi khí hậu",
        "organization": "Trung tâm Khuyến nông Quốc gia",
        "url": "https://khuyennongvn.gov.vn/thu-vien-khuyen-nong/thu-vien-sach-kn/tai-lieu-tap-huan-canh-tac-lua-thong-minh-thich-ung-voi-bien-doi-khi-hau-19488.html",
        "topic": "cultivation_water_nutrition",
    },
    {
        "source_id": "SRC_KN_RICE_LOW_EMISSION_31656",
        "title": "Kỹ thuật canh tác lúa chất lượng cao và phát thải thấp vùng đồng bằng sông Cửu Long",
        "organization": "Trung tâm Khuyến nông Quốc gia",
        "url": "https://khuyennongvn.gov.vn/khoa-hoc-cong-nghe/khcn-trong-nuoc/ky-thuat-canh-tac-lua-chat-luong-cao-va-phat-thai-thap-vung-dong-bang-song-cuu-long-31656.html",
        "topic": "cultivation_water_nutrition_ipm",
    },
    {
        "source_id": "SRC_KN_RICE_WEED_22126",
        "title": "Hướng dẫn biện pháp kỹ thuật quản lý lúa cỏ (lúa ma)",
        "organization": "Trung tâm Khuyến nông Quốc gia",
        "url": "https://khuyennongvn.gov.vn/khoa-hoc-cong-nghe/khcn-trong-nuoc/huong-dan-bien-phap-ky-thuat-quan-ly-lua-co-lua-ma-22126.html",
        "topic": "weed_management",
    },
    {
        "source_id": "SRC_VAAS_RICE_LEAF_BIOLOGY",
        "title": "Sinh học cây lúa: cấu tạo và đặc điểm lá lúa",
        "organization": "Viện Khoa học Nông nghiệp Việt Nam",
        "url": "https://vaas.vn/kienthuc/Caylua/10/031_lalua.htm",
        "topic": "rice_biology_varieties_cultivation",
    },
    {
        "source_id": "SRC_KN_RICE_ORGANIC_32601",
        "title": "Kết quả nhân rộng mô hình canh tác lúa hữu cơ vụ hè-thu 2026 tại An Giang",
        "organization": "Trung tâm Khuyến nông Quốc gia",
        "url": "https://khuyennongvn.gov.vn/khoa-hoc-cong-nghe/khcn-trong-nuoc/ket-qua-nhan-rong-mo-hinh-canh-tac-lua-huu-co-vu-he-thu-2026-tai-tinh-an-giang-32601.html",
        "topic": "organic_rice_sustainable_cultivation",
    },
    {
        "source_id": "SRC_KN_RICE_SALINITY_32567",
        "title": "Hướng dẫn kỹ thuật canh tác lúa trong điều kiện hạn, mặn vùng Đồng bằng sông Cửu Long",
        "organization": "Trung tâm Khuyến nông Quốc gia",
        "url": "https://khuyennongvn.gov.vn/thu-vien-khuyen-nong/thu-vien-sach-kn/huong-dan-ky-thuat-canh-tac-lua-va-cay-an-trai-trong-dieu-kien-han-man-vung-dong-bang-song-cuu-long-32567.html",
        "topic": "drought_salinity_water_management",
    },
    {
        "source_id": "SRC_KN_RICE_POSTHARVEST_31686",
        "title": "Quản lý thu hoạch và xử lý lúa sau thu hoạch",
        "organization": "Trung tâm Khuyến nông Quốc gia",
        "url": "https://khuyennongvn.gov.vn/1-trieu-hecta-chuyen-canh-lua-chat-luong-cao/quan-ly-thu-hoach-va-xu-ly-lua-sau-thu-hoach-31686.html",
        "topic": "harvest_postharvest",
    },
    {
        "source_id": "SRC_KN_RICE_SMART_PACKAGE_21736",
        "title": "Gói kỹ thuật trong canh tác lúa thông minh",
        "organization": "Trung tâm Khuyến nông Quốc gia",
        "url": "https://khuyennongvn.gov.vn/hoat-dong-khuyen-nong/chuyen-giao-tbkt/goi-ky-thuat-trong-canh-tac-lua-thong-minh-21736.html",
        "topic": "integrated_rice_cultivation_ipm",
    },
]


class TextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []
        self.skip = 0
        self.title: list[str] = []
        self.in_title = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        tag = tag.lower()
        if tag in {"script", "style", "noscript", "svg", "nav", "footer", "header"}:
            self.skip += 1
        if tag == "title":
            self.in_title = True
        if tag in {"p", "br", "li", "h1", "h2", "h3", "h4", "tr"} and not self.skip:
            self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if tag == "title":
            self.in_title = False
        if tag in {"script", "style", "noscript", "svg", "nav", "footer", "header"} and self.skip:
            self.skip -= 1

    def handle_data(self, data: str) -> None:
        if self.in_title:
            self.title.append(data)
        if not self.skip:
            text = re.sub(r"\s+", " ", data).strip()
            if text:
                self.parts.append(text)

    def result(self) -> tuple[str, str]:
        text = re.sub(r"\n\s*\n+", "\n", "\n".join(self.parts))
        text = re.sub(r"[ \t]+", " ", text)
        return " ".join(self.title).strip(), text.strip()


def now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def check_robots(url: str, user_agent: str) -> bool:
    parsed = urlparse(url)
    robots_url = f"{parsed.scheme}://{parsed.netloc}/robots.txt"
    parser = RobotFileParser(robots_url)
    try:
        parser.read()
        return parser.can_fetch(user_agent, url)
    except Exception:
        # A failed robots fetch is not permission to crawl. The record is
        # marked blocked so an operator can decide manually.
        return False


def fetch(source: dict[str, str], user_agent: str) -> dict[str, object]:
    url = source["url"]
    if urlparse(url).netloc not in {"khuyennongvn.gov.vn", "vaas.vn"}:
        raise ValueError(f"URL outside allowlist: {url}")
    if not check_robots(url, user_agent):
        raise RuntimeError(f"robots.txt disallows or could not be checked: {url}")

    response = requests.get(
        url,
        headers={"User-Agent": user_agent, "Accept-Language": "vi,en;q=0.8"},
        timeout=30,
    )
    response.raise_for_status()
    body = response.content
    digest = hashlib.sha256(body).hexdigest()
    filename = f"{source['source_id']}.html"
    (RAW / filename).write_bytes(body)

    root = lxml_html.fromstring(body)
    page_title = " ".join(root.xpath("//title//text()"))
    content_nodes = root.xpath(
        "//*[contains(concat(' ', normalize-space(@class), ' '), ' post-content ')]"
    )
    if not content_nodes:
        content_nodes = root.xpath("//body")
    content_node = content_nodes[0] if content_nodes else root
    for node in content_node.xpath(".//script|.//style|.//noscript|.//nav|.//footer|.//header"):
        node.drop_tree()
    text = "\n".join(
        line.strip()
        for line in content_node.text_content().splitlines()
        if line.strip()
    )
    text = re.sub(r"\n{2,}", "\n", text)
    if len(text) < 500:
        raise ValueError(f"Extracted text is too short for {url}: {len(text)} chars")

    topic_tags = {
        "cultivation_water_nutrition": ["gieo_cay", "quan_ly_nuoc", "dinh_duong"],
        "cultivation_water_nutrition_ipm": [
            "lam_dat", "giong", "gieo_sa", "quan_ly_nuoc", "dinh_duong", "ipm", "thu_hoach"
        ],
        "weed_management": ["lua_co", "co_dai", "quan_ly_tong_hop"],
        "rice_biology_varieties_cultivation": ["sinh_hoc_cay_lua", "giong", "sinh_truong"],
        "organic_rice_sustainable_cultivation": ["lua_huu_co", "ben_vung", "quan_ly_dinh_duong", "ipm"],
        "drought_salinity_water_management": ["han", "man", "quan_ly_nuoc", "thich_ung_khi_hau"],
        "harvest_postharvest": ["thu_hoach", "say", "bao_quan", "giam_that_thoat"],
        "integrated_rice_cultivation_ipm": ["lam_dat", "giong", "dinh_duong", "quan_ly_nuoc", "ipm", "thu_hoach"],
    }
    review_flags = ["IN_REVIEW", "CHECK_LICENSE_TERMS", "EXPERT_AGRONOMY_REVIEW"]
    if re.search(r"thuốc|hoạt chất|liều lượng|cách ly|4 đúng", text, re.IGNORECASE):
        review_flags.append("CONTAINS_TREATMENT_REFERENCES")
    if len(text) < 1500:
        review_flags.append("CATALOG_PAGE_NEEDS_CHILD_DOCUMENTS")

    record = {
        "document_id": source["source_id"],
        "title": source["title"],
        "content": text,
        "namespace": "farm",
        "document_type": "farm_knowledge_staging",
        "topic": source["topic"],
        "tags": topic_tags[source["topic"]],
        "crop": "rice",
        "region": "Việt Nam; cần giới hạn theo vùng nếu tài liệu có nêu địa phương",
        "season": "Không chuẩn hóa nếu nguồn không nêu rõ",
        "content_kind": "official_extension_or_research_article",
        "language": "vi",
        "provenance": {
            "source_ids": [source["source_id"]],
            "url": url,
            "accessed_at": now(),
            "sha256": digest,
            "raw_file": f"raw/{filename}",
            "page_title": page_title,
        },
        "review_status": "IN_REVIEW",
        "review_flags": review_flags,
        "content_stats": {
            "characters": len(text),
            "words": len(text.split()),
            "lines": len(text.splitlines()),
        },
        "safety_note": "Không dùng làm nguồn thuốc, liều hoặc PHI nếu chưa được duyệt riêng.",
    }
    return record


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--delay", type=float, default=2.0)
    parser.add_argument("--limit", type=int, default=len(SOURCES))
    args = parser.parse_args()
    RAW.mkdir(parents=True, exist_ok=True)
    PROCESSED.mkdir(parents=True, exist_ok=True)

    user_agent = "DX-Farm-RAG-staging/0.1 (contact: project-operator)"
    registry: list[dict[str, object]] = []
    documents: list[dict[str, object]] = []
    errors: list[dict[str, str]] = []
    for index, source in enumerate(SOURCES[: args.limit]):
        try:
            documents.append(fetch(source, user_agent))
            registry.append(
                {
                    **source,
                    "source_type": "Vietnamese agricultural extension or research source",
                    "recommended_use": "Tham khảo kiến thức canh tác lúa; cần chuyên gia duyệt trước khi ingest.",
                    "authority_level": "Official Vietnamese extension/research source",
                    "accessed_at": documents[-1]["provenance"]["accessed_at"],
                    "license": "Review required",
                    "crop_target_locale": "Lúa; Việt Nam",
                    "review_status": "IN_REVIEW",
                }
            )
            print(f"OK {source['source_id']}")
        except Exception as exc:
            errors.append({"source_id": source["source_id"], "url": source["url"], "error": str(exc)})
            print(f"BLOCKED {source['source_id']}: {exc}")
        if index + 1 < min(args.limit, len(SOURCES)):
            time.sleep(args.delay)

    (PROCESSED / "farm_knowledge_staging.json").write_text(
        json.dumps(documents, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (STAGING / "source_registry.json").write_text(
        json.dumps(registry, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (STAGING / "crawl_report.json").write_text(
        json.dumps(
            {"created_at": now(), "documents": len(documents), "errors": errors},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    review_queue = [
        {
            "document_id": item["document_id"],
            "title": item["title"],
            "source_ids": item["provenance"]["source_ids"],
            "topic": item["topic"],
            "review_status": item["review_status"],
            "review_flags": item["review_flags"],
            "review_questions": [
                "Nguồn có được phép lưu và sử dụng nội dung cho RAG không?",
                "Nội dung có phù hợp với điều kiện Việt Nam và phạm vi topic không?",
                "Có đoạn thuốc/liều/PHI cần loại khỏi corpus giải thích không?",
            ],
        }
        for item in documents
    ]
    (STAGING / "review_queue.json").write_text(
        json.dumps(review_queue, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"Staged documents: {len(documents)}; blocked: {len(errors)}")
    print(f"Output: {STAGING}")


if __name__ == "__main__":
    main()
