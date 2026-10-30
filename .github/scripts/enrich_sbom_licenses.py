# SPDX-License-Identifier: Apache-2.0
"""Fill the licenses of a CycloneDX file whose components have none (the Python SBOM).

    python3 .github/scripts/enrich_sbom_licenses.py sbom/ai.cdx.json

Each package is looked up on PyPI at its exact version.
A package whose license cannot be mapped to an SPDX identifier is listed and left empty,
so the audit in check_licenses.py fails on it until it is added to OVERRIDES with its source.
"""
import json
import re
import sys
import urllib.error
import urllib.request

CLASSIFIERS = {
    "License :: OSI Approved :: MIT License": "MIT",
    "License :: OSI Approved :: Apache Software License": "Apache-2.0",
    "License :: OSI Approved :: ISC License (ISCL)": "ISC",
    "License :: OSI Approved :: Mozilla Public License 2.0 (MPL 2.0)": "MPL-2.0",
    "License :: OSI Approved :: Python Software Foundation License": "PSF-2.0",
    "License :: OSI Approved :: BSD License": "BSD-3-Clause",
    "License :: OSI Approved :: GNU Lesser General Public License v3 or later (LGPLv3+)": "LGPL-3.0-or-later",
    "License :: OSI Approved :: GNU Lesser General Public License v2 or later (LGPLv2+)": "LGPL-2.1-or-later",
    "License :: OSI Approved :: The Unlicense (Unlicense)": "Unlicense",
    "License :: CC0 1.0 Universal (CC0 1.0) Public Domain Dedication": "CC0-1.0",
    "License :: OSI Approved :: zlib/libpng License": "Zlib",
}

TEXT = {
    "mit": "MIT",
    "mit license": "MIT",
    "apache": "Apache-2.0",
    "apache 2.0": "Apache-2.0",
    "apache-2.0": "Apache-2.0",
    "apache software license": "Apache-2.0",
    "apache license 2.0": "Apache-2.0",
    "apache license, version 2.0": "Apache-2.0",
    "bsd": "BSD-3-Clause",
    "bsd license": "BSD-3-Clause",
    "bsd-3-clause": "BSD-3-Clause",
    "bsd 3-clause": "BSD-3-Clause",
    "bsd-2-clause": "BSD-2-Clause",
    "isc": "ISC",
    "mpl-2.0": "MPL-2.0",
    "psf-2.0": "PSF-2.0",
    "python software foundation license": "PSF-2.0",
}

# Packages whose PyPI metadata does not carry a usable license; each value was read from the project's own license file.
OVERRIDES: dict[str, str] = {
    "sniffio": "MIT OR Apache-2.0",  # LICENSE.MIT and LICENSE.APACHE2 in the project
    "torch": "BSD-3-Clause",  # the +cpu build is not on PyPI; LICENSE of pytorch/pytorch
    "torchvision": "BSD-3-Clause",  # LICENSE of pytorch/vision
    "tqdm": "MPL-2.0 AND MIT",  # LICENCE of tqdm/tqdm
    "transformers": "Apache-2.0",  # LICENSE of huggingface/transformers
}


def pypi(name: str, version: str) -> dict:
    url = f"https://pypi.org/pypi/{name}/{version}/json"
    with urllib.request.urlopen(url, timeout=30) as response:
        return json.load(response)["info"]


def resolve(name: str, version: str) -> str | None:
    if name.lower() in OVERRIDES:
        return OVERRIDES[name.lower()]
    try:
        info = pypi(name, version)
    except urllib.error.HTTPError:
        return None
    if info.get("license_expression"):
        return info["license_expression"]
    found = {CLASSIFIERS[c] for c in info.get("classifiers", []) if c in CLASSIFIERS}
    if len(found) == 1:
        return found.pop()
    text = (info.get("license") or "").strip()
    if text and len(text) <= 60:
        return TEXT.get(text.lower(), text if re.fullmatch(r"[A-Za-z0-9.\-+ ()]+", text) and " " not in text else None)
    return None


def main(path: str) -> None:
    bom = json.load(open(path, encoding="utf-8"))
    unresolved = []
    for component in bom.get("components", []):
        if component.get("licenses"):
            continue
        spdx = resolve(component["name"], component["version"])
        if not spdx:
            unresolved.append(f"{component['name']}=={component['version']}")
            continue
        key = "expression" if re.search(r"\b(AND|OR|WITH)\b", spdx) else "id"
        component["licenses"] = [{"expression": spdx} if key == "expression" else {"license": {"id": spdx}}]
    with open(path, "w", encoding="utf-8") as out:
        json.dump(bom, out, indent=2, ensure_ascii=False)
        out.write("\n")
    print(f"unresolved: {len(unresolved)}")
    for item in unresolved:
        print("  ", item)


if __name__ == "__main__":
    main(sys.argv[1])
