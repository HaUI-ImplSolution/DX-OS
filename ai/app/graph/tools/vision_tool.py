# SPDX-License-Identifier: Apache-2.0
def predict(predictor, image_bytes: bytes) -> dict:
    """Call the injected CV service without loading a model inside LangGraph."""

    return predictor.predict(image_bytes)
