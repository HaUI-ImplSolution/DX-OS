# SPDX-License-Identifier: AGPL-3.0-only
import re


MEDICATION = re.compile(
    r"thuốc|hoạt\s*chất|liều|\bphi\b|cách\s*ly|nồng\s*độ|\bh[oó]a\s*học|\bhoá\s*học|phun|"
    r"\b(?:ml|mg|kg|g|l)\s*/\s*(?:ha|l|lít|sào)|\b\w+\s*\d+(?:\.\d+)?\s*(?:sc|ec|wp|wg)\b",
    re.IGNORECASE,
)


def redact_medication_lines(content: str) -> str:
    """Remove evidence lines containing medication, dose, or PHI instructions."""
    return "\n".join(line for line in content.splitlines() if not MEDICATION.search(line)).strip()
