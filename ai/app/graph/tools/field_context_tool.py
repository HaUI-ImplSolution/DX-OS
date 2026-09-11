# SPDX-License-Identifier: Apache-2.0
import uuid

from app.services.field_context_service import get_field_context


def load(db, parcel_code: str | None, crop_season_id: str | None) -> dict:
    return get_field_context(
        db,
        parcel_code,
        uuid.UUID(crop_season_id) if crop_season_id else None,
    )
