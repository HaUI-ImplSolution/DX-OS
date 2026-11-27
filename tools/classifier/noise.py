# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0

import re
import unicodedata


ABBREVIATIONS = {
    "em": "e", "anh": "a", "chị": "c", "không": "ko", "được": "dc",
    "sinh viên": "sv", "giảng viên": "gv", "học kỳ": "hk", "trước": "trc",
    "với": "vs", "rồi": "r", "bác sĩ": "bs", "sức khỏe": "sk", "kết quả": "kq",
}
ABBREVIATION_RE = re.compile(
    r"\b(?:" + "|".join(map(re.escape, ABBREVIATIONS)) + r")\b", re.IGNORECASE
)
TELEX = dict(zip("ăâêôơưđ", ("aw", "aa", "ee", "oo", "ow", "uw", "dd")))
TONE = dict(zip("\u0301\u0300\u0309\u0303\u0323", "sfrxj"))
METHOD_WEIGHTS = {
    "no_accents": 22, "partial_accents": 20, "abbreviate": 14,
    "telex": 12, "repeat_or_drop": 12, "case": 10, "punctuation": 10,
}
VARIANTS_PER_ORIGINAL = 4


# Remove combining accents and fold đ and Đ to d and D.
def unaccent(text):
    return "".join(c for c in unicodedata.normalize("NFD", text)
                   if not unicodedata.combining(c)).translate(str.maketrans("đĐ", "dD"))


# Remove accents from randomly selected characters.
def partial_accents(text, rng):
    return "".join(unaccent(c) if rng.random() < 0.45 else c for c in text)


# Replace some matching words and phrases with their abbreviations.
def abbreviate(text, rng):
    return ABBREVIATION_RE.sub(
        lambda match: ABBREVIATIONS[match[0].lower()] if rng.random() < 0.75 else match[0],
        text,
    )


# Rewrite one randomly selected accented word as Telex keystrokes.
def telex(text, rng):
    words = list(re.finditer(r"[^\W\d_]+", text))
    accented = [word for word in words if word[0] != unaccent(word[0])]
    if not accented:
        return text
    word = rng.choice(accented)
    letters = []
    tone = ""
    for character in word[0].lower():
        parts = unicodedata.normalize("NFD", character)
        tone += "".join(TONE[c] for c in parts if c in TONE)
        vowel = unicodedata.normalize("NFC", "".join(c for c in parts if c not in TONE))
        letters.append(TELEX.get(vowel, vowel))
    return text[:word.start()] + "".join(letters) + tone + text[word.end():]


# Duplicate or remove one randomly selected letter.
def repeat_or_drop(text, rng):
    positions = [i for i, char in enumerate(text) if char.isalpha()]
    if not positions:
        return text
    i = rng.choice(positions)
    replacement = text[i] * 2 if rng.random() < 0.5 else ""
    return text[:i] + replacement + text[i + 1:]


# Randomly lowercase, uppercase, or swap the case of the text.
def change_case(text, rng):
    return rng.choice((str.lower, str.upper, str.swapcase))(text)


# Replace punctuation with spaces and collapse whitespace.
def remove_punctuation(text, rng):
    return " ".join("".join(" " if unicodedata.category(c).startswith("P") else c
                            for c in text).split())


METHODS = {
    "no_accents": lambda text, rng: unaccent(text),
    "partial_accents": partial_accents,
    "abbreviate": abbreviate,
    "telex": telex,
    "repeat_or_drop": repeat_or_drop,
    "case": change_case,
    "punctuation": remove_punctuation,
}


# Yield up to four distinct noisy variants of a row in the same family.
def variants(row, rng):
    fields = [key for key in ("title", "text") if key in row]
    originals = tuple(unicodedata.normalize("NFC", row[key]) for key in fields)
    seen = {originals}
    for _ in range(VARIANTS_PER_ORIGINAL * 5):
        name = rng.choices(tuple(METHOD_WEIGHTS), weights=METHOD_WEIGHTS.values())[0]
        changed = tuple(METHODS[name](value, rng) for value in originals)
        if changed in seen or not any(value.strip() for value in changed):
            continue
        seen.add(changed)
        yield {**row, **dict(zip(fields, changed, strict=True)),
               "id": f"{row['id']}-v{len(seen) - 1}", "style": "cau_tha", "noise": name}
        if len(seen) == VARIANTS_PER_ORIGINAL + 1:
            return
