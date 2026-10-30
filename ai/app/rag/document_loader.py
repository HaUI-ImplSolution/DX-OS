# SPDX-License-Identifier: AGPL-3.0-only
from pathlib import Path

from docx import Document
from pypdf import PdfReader


def load_pdf(file_path: str) -> list[dict]:
    reader = PdfReader(file_path)

    pages = []

    for page_number, page in enumerate(reader.pages, start=1):
        text = page.extract_text() or ""

        pages.append({
            "page_number": page_number,
            "content": text.strip(),
        })

    return pages


def load_docx(file_path: str) -> list[dict]:
    doc = Document(file_path)

    text = "\n".join(
        paragraph.text
        for paragraph in doc.paragraphs
        if paragraph.text.strip()
    )

    return [{
        "page_number": None,
        "content": text,
    }]


def load_document(file_path: str) -> list[dict]:
    suffix = Path(file_path).suffix.lower()

    if suffix == ".pdf":
        return load_pdf(file_path)

    if suffix == ".docx":
        return load_docx(file_path)

    raise ValueError(
        f"Unsupported document type: {suffix}"
    )
