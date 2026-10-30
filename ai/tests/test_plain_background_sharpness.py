# SPDX-License-Identifier: AGPL-3.0-only
from io import BytesIO

import numpy as np
import pytest
from PIL import Image, ImageDraw, ImageFilter

from app.cv.policy import MIN_SHARPNESS
from app.services.plant_disease_service import image_sharpness, inspect_image


def encode(image):
    stream = BytesIO()
    image.save(stream, format="PNG")
    return stream.getvalue()


def leaf_on_white():
    """A small textured leaf in the middle of a plain white background."""
    image = Image.new("RGB", (800, 600), (250, 250, 250))
    draw = ImageDraw.Draw(image)
    draw.ellipse((330, 180, 470, 420), fill=(60, 130, 50), outline=(20, 70, 20), width=3)
    draw.line([(400, 180), (400, 420)], fill=(190, 220, 120), width=4)
    for y in range(205, 400, 20):
        draw.line([(400, y), (460, y + 12)], fill=(30, 90, 35), width=3)
        draw.line([(400, y), (340, y + 12)], fill=(30, 90, 35), width=3)
    return image


def test_plain_background_is_the_case_the_tile_score_alone_rejects():
    grayscale = leaf_on_white().convert("L")

    assert image_sharpness(grayscale) < MIN_SHARPNESS


def test_sharp_leaf_on_plain_background_passes():
    quality = inspect_image(encode(leaf_on_white()))[1]

    assert quality["passed"] is True
    assert quality["reasons"] == []


@pytest.mark.parametrize("radius", [3, 6])
def test_blurred_leaf_on_plain_background_is_rejected(radius):
    blurred = leaf_on_white().filter(ImageFilter.GaussianBlur(radius))

    quality = inspect_image(encode(blurred))[1]

    assert quality["passed"] is False
    assert any("mờ" in reason for reason in quality["reasons"])


@pytest.mark.parametrize("noise", [12, 24])
def test_noise_does_not_make_a_blurred_plain_background_photo_pass(noise):
    pixels = np.asarray(leaf_on_white().filter(ImageFilter.GaussianBlur(4)), dtype=np.float32)
    grain = np.random.default_rng(3).normal(0, noise, (600, 800, 1))
    noisy = Image.fromarray(np.clip(pixels + grain, 0, 255).astype(np.uint8))

    quality = inspect_image(encode(noisy))[1]

    assert quality["passed"] is False


def test_featureless_mid_gray_image_is_rejected():
    quality = inspect_image(encode(Image.new("RGB", (640, 480), (128, 128, 128))))[1]

    assert quality["passed"] is False
