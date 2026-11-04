# SPDX-License-Identifier: AGPL-3.0-only
from pathlib import Path

from app.core.config import get_settings
from huggingface_hub import snapshot_download
from huggingface_hub.errors import LocalEntryNotFoundError

PATTERNS = ["*.json", "model.safetensors", "tokenizer*", "vocab*", "*.model", "*.txt"]


def main():
    name = get_settings().embedding_model
    if Path(name).is_dir():
        print("Embedding model is available locally")
        return
    try:
        cached = Path(snapshot_download(name, local_files_only=True))
        if (
            not (cached / "model.safetensors").is_file()
            and not (cached / "pytorch_model.bin").is_file()
        ):
            raise LocalEntryNotFoundError("Weights are not cached")
        print("Embedding model is cached")
    except LocalEntryNotFoundError:
        from huggingface_hub import list_repo_files

        weights = (
            "model.safetensors"
            if "model.safetensors" in list_repo_files(name)
            else "pytorch_model.bin"
        )
        snapshot_download(name, allow_patterns=[*PATTERNS, weights])
        print("Embedding model prepared")


if __name__ == "__main__":
    main()
