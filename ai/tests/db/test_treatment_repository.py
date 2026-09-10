import math
import uuid
from datetime import datetime, timezone
from types import SimpleNamespace

import pytest

from app.db.repositories import treatment_repository


def test_create_treatment_from_rule_uses_approved_actual_dose(monkeypatch):
    rule = SimpleNamespace(
        id=uuid.uuid4(),
        pesticide_id=uuid.uuid4(),
        dose_min=2.0,
        dose_max=2.5,
        dose_unit="L/ha",
        phi_days=5,
    )
    monkeypatch.setattr(
        treatment_repository,
        "create_treatment_application",
        lambda **values: values,
    )

    result = treatment_repository.create_treatment_from_rule(
        db=object(),
        crop_season_id=uuid.uuid4(),
        rule=rule,
        actual_dose=2.2,
        applied_at=datetime.now(timezone.utc),
    )

    assert result["dose"] == 2.2
    assert result["dose_unit"] == "L/ha"
    assert result["phi_days"] == 5


@pytest.mark.parametrize("actual_dose", [1.9, 2.6, math.nan])
def test_create_treatment_from_rule_rejects_out_of_range_dose(
    actual_dose,
):
    rule = SimpleNamespace(
        id=uuid.uuid4(),
        pesticide_id=uuid.uuid4(),
        dose_min=2.0,
        dose_max=2.5,
        dose_unit="L/ha",
        phi_days=5,
    )

    with pytest.raises(ValueError, match="actual_dose"):
        treatment_repository.create_treatment_from_rule(
            db=object(),
            crop_season_id=uuid.uuid4(),
            rule=rule,
            actual_dose=actual_dose,
            applied_at=datetime.now(timezone.utc),
        )
