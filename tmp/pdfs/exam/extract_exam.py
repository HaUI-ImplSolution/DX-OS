from pathlib import Path
from pypdf import PdfReader

pdf = Path(r"C:\Users\lam05\Desktop\HIT\DX-Farm\development\docs\De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf")
out = Path(r"C:\Users\lam05\Desktop\HIT\DX-Farm\tmp\pdfs\exam\exam.txt")
reader = PdfReader(str(pdf))
parts = []
for index, page in enumerate(reader.pages, 1):
    parts.append(f"\n===== PAGE {index} =====\n")
    parts.append(page.extract_text(extraction_mode="layout") or "")
out.write_text("\n".join(parts), encoding="utf-8")
print(f"pages={len(reader.pages)}")
print(out)
