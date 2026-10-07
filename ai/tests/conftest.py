import os

from PIL import Image, ImageDraw

# app.db.session builds its engine at import time; the URL is never connected to in unit tests.
os.environ.setdefault("DATABASE_URL", "postgresql+psycopg://test:test@localhost:5432/test")


def leaf_scene():
    """Deterministic leaf-like detail; independent of camera data and model weights."""
    image = Image.new("RGB", (640, 480), (95, 125, 65))
    draw = ImageDraw.Draw(image)
    for x in range(-40, 680, 80):
        draw.polygon([(x, 480), (x + 24, 0), (x + 66, 480)], fill=(35, 105, 38))
        draw.line([(x + 25, 0), (x + 32, 480)], fill=(145, 180, 80), width=3)
        for y in range(20, 480, 14):
            draw.line([(x + 31, y), (x + 54, y + 10)], fill=(90, 150, 55), width=2)
            draw.ellipse((x + 30, y + 3, x + 39, y + 11), fill=(165, 105, 55))
    return image
