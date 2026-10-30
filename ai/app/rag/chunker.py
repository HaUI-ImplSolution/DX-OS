# SPDX-License-Identifier: AGPL-3.0-only
from textwrap import wrap


def chunk_text(
    text: str,
    max_chars: int = 350,
    overlap_lines: int = 1,
) -> list[str]:
    """Split field-oriented text into chunks without exceeding max_chars."""

    if max_chars <= 0:
        raise ValueError("max_chars must be greater than 0")
    if overlap_lines < 0:
        raise ValueError("overlap_lines must be greater than or equal to 0")

    lines = [
        segment
        for line in text.splitlines()
        if line.strip()
        for segment in wrap(
            line.strip(),
            width=max_chars,
            break_long_words=True,
            break_on_hyphens=False,
        )
    ]
    if not lines:
        return []

    chunks: list[str] = []
    current: list[str] = []

    for line in lines:
        candidate = "\n".join([*current, line])
        if current and len(candidate) > max_chars:
            chunks.append("\n".join(current))
            current = current[-overlap_lines:] if overlap_lines else []

            while current and len("\n".join([*current, line])) > max_chars:
                current.pop(0)

        current.append(line)

    if current:
        chunks.append("\n".join(current))

    return chunks
