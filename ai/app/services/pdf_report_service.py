# SPDX-License-Identifier: Apache-2.0
import os
import uuid
from datetime import datetime
from typing import Any

import pymupdf


def generate_diagnosis_pdf_report(
    cv_result: dict[str, Any],
    treatment_knowledge: dict[str, Any],
    rag_knowledge: list[dict[str, Any]],
) -> bytes:
    """Generate a clean, structured agricultural diagnosis PDF report using PyMuPDF."""
    doc = pymupdf.open()
    page = doc.new_page(width=595, height=842)  # Standard A4 (595 x 842 points)

    # 1. Setup Font (Use Windows Arial/Segoe UI if available for UTF-8 Vietnamese diacritics, else fallback)
    font_path = None
    for candidate in [
        "C:/Windows/Fonts/arial.ttf",
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/times.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]:
        if os.path.exists(candidate):
            font_path = candidate
            break

    if font_path:
        page.insert_font(fontname="mainfont", fontfile=font_path)
        font_name = "mainfont"
    else:
        font_name = "helv"

    # Colors
    PRIMARY_COLOR = (0.08, 0.45, 0.23)    # Deep Green
    SECONDARY_COLOR = (0.15, 0.2, 0.3)    # Dark Navy
    ACCENT_COLOR = (0.9, 0.3, 0.1)       # Coral Accent
    GRAY_TEXT = (0.35, 0.35, 0.35)

    y = 40.0

    # 2. Header Banner & Title
    # Draw top decorative bar
    page.draw_rect(pymupdf.Rect(40, y, 555, y + 6), color=PRIMARY_COLOR, fill=PRIMARY_COLOR)
    y += 20

    page.insert_text((40, y), "HỆ THỐNG NÔNG NGHIỆP SỐ DX-FARM", fontname=font_name, fontsize=14, color=PRIMARY_COLOR)
    y += 18
    page.insert_text((40, y), "BÁO CÁO CHẨN ĐOÁN VÀ TRUY VẤN TRI THỨC BỆNH CÂY TRỒNG", fontname=font_name, fontsize=12, color=SECONDARY_COLOR)
    y += 14

    report_id = f"RPT-{uuid.uuid4().hex[:8].upper()}"
    created_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    page.insert_text((40, y), f"Mã báo cáo: {report_id}  |  Thời gian khởi tạo: {created_at}", fontname=font_name, fontsize=9, color=GRAY_TEXT)
    y += 15

    page.draw_line(pymupdf.Point(40, y), pymupdf.Point(555, y), color=(0.8, 0.8, 0.8), width=0.8)
    y += 20

    # Helper function for Section Headers
    def draw_section_header(title: str, current_y: float) -> float:
        page.draw_rect(pymupdf.Rect(40, current_y, 555, current_y + 20), color=(0.93, 0.96, 0.93), fill=(0.93, 0.96, 0.93))
        page.insert_text((46, current_y + 14), title, fontname=font_name, fontsize=11, color=PRIMARY_COLOR)
        return current_y + 28

    # 3. Section 1: Result from Computer Vision
    y = draw_section_header("I. KẾT QUẢ NHẬN DIỆN BỆNH (COMPUTER VISION)", y)

    disease_code = cv_result.get("disease_code", "Unknown")
    disease_name = cv_result.get("disease_name", "N/A")
    confidence = cv_result.get("confidence", 0.0)
    conf_percent = f"{confidence * 100:.1f}%" if isinstance(confidence, (float, int)) and confidence <= 1.0 else f"{confidence}%"
    model_name = cv_result.get("model_name", "EfficientNet-B4")
    model_version = cv_result.get("model_version", "v1.0")

    page.insert_text((55, y), f"• Mã bệnh (Disease Code):", fontname=font_name, fontsize=10, color=SECONDARY_COLOR)
    page.insert_text((220, y), str(disease_code), fontname=font_name, fontsize=10, color=PRIMARY_COLOR)
    y += 16
    page.insert_text((55, y), f"• Tên bệnh (Vietnamese Name):", fontname=font_name, fontsize=10, color=SECONDARY_COLOR)
    page.insert_text((220, y), str(disease_name), fontname=font_name, fontsize=10, color=SECONDARY_COLOR)
    y += 16
    page.insert_text((55, y), f"• Độ tin cậy (Confidence):", fontname=font_name, fontsize=10, color=SECONDARY_COLOR)
    page.insert_text((220, y), conf_percent, fontname=font_name, fontsize=10, color=ACCENT_COLOR if confidence < 0.7 else PRIMARY_COLOR)
    y += 16
    page.insert_text((55, y), f"• Mô hình CV:", fontname=font_name, fontsize=10, color=SECONDARY_COLOR)
    page.insert_text((220, y), f"{model_name} (Phiên bản: {model_version})", fontname=font_name, fontsize=10, color=GRAY_TEXT)
    y += 24

    # 4. Section 2: Structured Treatment & Policy Knowledge
    y = draw_section_header("II. QUY TRÌNH ĐIỀU TRỊ & CHÍNH SÁCH BẢO VỆ (STRUCTURED KNOWLEDGE)", y)

    path = treatment_knowledge.get("path", "review_required")
    page.insert_text((55, y), f"• Trạng thái đề xuất (Path):", fontname=font_name, fontsize=10, color=SECONDARY_COLOR)
    page.insert_text((220, y), str(path).upper(), fontname=font_name, fontsize=10, color=PRIMARY_COLOR)
    y += 18

    rules = treatment_knowledge.get("rules") or []
    policy = treatment_knowledge.get("policy")

    if rules:
        page.insert_text((55, y), "Danh sách thuốc & phác đồ hóa chất được cho phép:", fontname=font_name, fontsize=10, color=SECONDARY_COLOR)
        y += 16
        for idx, rule in enumerate(rules, 1):
            pesticide = rule.get("pesticide", "N/A")
            active_ing = rule.get("active_ingredient", "N/A")
            dose_min = rule.get("dose_min", 0)
            dose_max = rule.get("dose_max", 0)
            dose_unit = rule.get("dose_unit", "liều")
            phi_days = rule.get("phi_days", 0)

            rule_text = (
                f"  {idx}. Thuốc: {pesticide} (Hoạt chất: {active_ing})\n"
                f"     - Khoảng liều: {dose_min} - {dose_max} {dose_unit}\n"
                f"     - Thời gian cách ly thu hoạch (PHI): {phi_days} ngày"
            )
            rect = pymupdf.Rect(65, y, 545, y + 45)
            page.insert_textbox(rect, rule_text, fontname=font_name, fontsize=9.5, color=SECONDARY_COLOR)
            y += 48

    elif policy:
        page.insert_text((55, y), "Chính sách quản lý canh tác không hóa chất:", fontname=font_name, fontsize=10, color=SECONDARY_COLOR)
        y += 16
        m_status = policy.get("management_status", "N/A")
        c_scope = policy.get("chemical_rule_scope", "N/A")
        notes = policy.get("notes", "")

        policy_text = (
            f"  - Trạng thái quản lý: {m_status}\n"
            f"  - Phạm vi hóa chất: {c_scope}\n"
            f"  - Ghi chú chính sách: {notes}"
        )
        rect = pymupdf.Rect(65, y, 545, y + 45)
        page.insert_textbox(rect, policy_text, fontname=font_name, fontsize=9.5, color=SECONDARY_COLOR)
        y += 48
    else:
        page.insert_text((55, y), "Chưa có quy tắc điều trị tự động. Yêu cầu kỹ sư nông nghiệp đánh giá trực tiếp.", fontname=font_name, fontsize=9.5, color=ACCENT_COLOR)
        y += 24

    y += 10

    # 5. Section 3: RAG Knowledge Explanations
    y = draw_section_header("III. TÀI LIỆU VÀ BẰNG CHỨNG GIẢI THÍCH (RAG KNOWLEDGE)", y)

    if rag_knowledge:
        for idx, chunk in enumerate(rag_knowledge, 1):
            doc_title = chunk.get("document_title") or f"Tài liệu #{chunk.get('document_id', idx)}"
            content = chunk.get("content", "").strip()
            source_ids = ", ".join(chunk.get("source_ids") or [])
            score = chunk.get("score") or (1.0 - float(chunk.get("distance", 0.0)))
            score_str = f"{score * 100:.1f}%" if isinstance(score, float) else str(score)

            header_str = f"Chunk #{idx}: {doc_title} (Độ tương đồng: {score_str})"
            if source_ids:
                header_str += f" | Nguồn: {source_ids}"

            page.insert_text((55, y), header_str, fontname=font_name, fontsize=9.5, color=PRIMARY_COLOR)
            y += 14

            # Insert snippet content wrapped in textbox
            rect = pymupdf.Rect(65, y, 545, y + 60)
            page.insert_textbox(rect, content, fontname=font_name, fontsize=9, color=SECONDARY_COLOR)
            y += 68
    else:
        page.insert_text((55, y), "Không tìm thấy đoạn tài liệu RAG giải thích bổ sung.", fontname=font_name, fontsize=9.5, color=GRAY_TEXT)
        y += 24

    # 6. Footer Disclaimer & Pokayoke Warning
    footer_y = 780.0
    page.draw_line(pymupdf.Point(40, footer_y), pymupdf.Point(555, footer_y), color=(0.8, 0.8, 0.8), width=0.8)
    footer_y += 10

    disclaimer = (
        "LƯU Ý POKA-YOKE: Báo cáo này được tự động tạo từ hệ thống AI (CV + Tra cứu SQL + RAG).\n"
        "Mọi đề xuất phun thuốc và liều lượng bắt buộc phải qua Kỹ sư nông nghiệp phê duyệt (Human-in-the-loop) trước khi áp dụng thực tế."
    )
    page.insert_textbox(pymupdf.Rect(40, footer_y, 555, footer_y + 35), disclaimer, fontname=font_name, fontsize=8, color=ACCENT_COLOR)

    return doc.tobytes()
