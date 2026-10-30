# SPDX-License-Identifier: AGPL-3.0-only
"""Runs the production CV pipeline on a labelled image folder and prints a per-class summary.

    python -m scripts.evaluate_cv DIR

DIR holds one sub-folder per class, named like the model label in
data/structured/class_contract.json (e.g. "Blast", "Brown Spot"). Any other folder name is
treated as an untrained class; confident predictions for it are counted as wrong.
The healthy column counts Healthy predictions, which also enter the correct/wrong totals.
"""

import argparse
import hashlib
from collections import Counter
from pathlib import Path

from app.core.config import get_settings
from app.cv.contract import class_contract
from app.cv.policy import WORKFLOW_CONFIDENCE_THRESHOLD

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("directory", type=Path)
    directory = parser.parse_args().directory

    settings = get_settings()
    if not settings.cv_weights_sha256:
        settings.cv_weights_sha256 = hashlib.sha256(settings.cv_weights_path.read_bytes()).hexdigest()
    if not settings.crop_classifier_sha256:
        settings.crop_classifier_sha256 = hashlib.sha256(settings.crop_classifier_path.read_bytes()).hexdigest()

    from app.cv.crop_classifier import load_crop_classifier
    from app.cv.loader import load_model
    from app.services.plant_disease_service import PlantDiseaseService, assess_image

    contract = class_contract()
    code_by_label = {c["model_label"].lower(): c["disease_code"] for c in contract["classes"]}
    crop = load_crop_classifier()
    service = PlantDiseaseService(load_model(settings), crop, settings)

    header = f"{'class':<16}{'n':>4}{'rejected':>10}{'healthy':>9}{'correct':>9}{'wrong':>7}{'>=thr':>7}{'wrong>=thr':>12}"
    print(header)
    for folder in sorted(p for p in directory.iterdir() if p.is_dir()):
        expected = code_by_label.get(folder.name.lower())
        tally = Counter()
        for path in sorted(p for p in folder.iterdir() if p.suffix.lower() in IMAGE_SUFFIXES):
            data = path.read_bytes()
            tally["n"] += 1
            if not assess_image(data, crop)[1]["passed"]:
                tally["rejected"] += 1
                continue
            result = service.predict(data)
            code, confident = result["disease_code"], result["confidence"] >= WORKFLOW_CONFIDENCE_THRESHOLD
            if code == "Rice___healthy":
                tally["healthy"] += 1
            if code == expected:
                tally["correct"] += 1
            else:
                tally["wrong"] += 1
            if confident:
                tally[">=thr"] += 1
                if code != expected:
                    tally["wrong>=thr"] += 1
        print(
            f"{folder.name:<16}{tally['n']:>4}{tally['rejected']:>10}{tally['healthy']:>9}"
            f"{tally['correct']:>9}{tally['wrong']:>7}{tally['>=thr']:>7}{tally['wrong>=thr']:>12}"
        )


if __name__ == "__main__":
    main()
