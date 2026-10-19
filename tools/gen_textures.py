#!/usr/bin/env python3
"""Generates the placeholder textures (entity skin and spawn egg icon).

Run from the repository root: python3 tools/gen_textures.py
"""
import random
from pathlib import Path

from PIL import Image

OUT = Path("src/client/resources/assets/giantess/textures")

SKIN = (222, 178, 140)
HAIR = (92, 58, 40)
TUNIC = (46, 120, 120)
TUNIC_TRIM = (30, 80, 85)
TROUSERS = (70, 62, 90)
BOOTS = (58, 40, 30)
EYE_WHITE = (240, 240, 240)
IRIS = (60, 140, 90)
MOUTH = (170, 90, 90)


def jitter(rng, color, amount=5):
	shift = rng.randint(-amount, amount)
	return tuple(max(0, min(255, c + shift)) for c in color) + (255,)


def paint_box(img, rng, u, v, w, h, d, painter):
	"""Paints the six faces of a model cuboid whose texture origin is (u, v)."""
	faces = {
		"top": (u + d, v, w, d),
		"bottom": (u + d + w, v, w, d),
		"right": (u, v + d, d, h),
		"front": (u + d, v + d, w, h),
		"left": (u + d + w, v + d, d, h),
		"back": (u + d + w + d, v + d, w, h),
	}
	for name, (x0, y0, fw, fh) in faces.items():
		for fy in range(fh):
			for fx in range(fw):
				img.putpixel((x0 + fx, y0 + fy), jitter(rng, painter(name, fx, fy, fw, fh)))


def head(face, x, y, w, h):
	if face == "top":
		return HAIR
	if face == "bottom":
		return SKIN
	if face == "back":
		return HAIR if y < 6 else SKIN
	if face == "right":
		return HAIR if y < 3 or x <= 2 else SKIN
	if face == "left":
		return HAIR if y < 3 or x >= 5 else SKIN
	# front
	if y < 2 or (y < 4 and x in (0, 7)):
		return HAIR
	if y == 4 and x in (1, 6):
		return EYE_WHITE
	if y == 4 and x in (2, 5):
		return IRIS
	if y == 6 and x in (3, 4):
		return MOUTH
	return SKIN


def body(face, x, y, w, h):
	if face == "top":
		return SKIN
	if face == "bottom":
		return TROUSERS
	return TUNIC_TRIM if y == 9 else TUNIC


def arm(face, x, y, w, h):
	if face == "top":
		return TUNIC
	if face == "bottom":
		return SKIN
	return TUNIC if y < 5 else SKIN


def leg(face, x, y, w, h):
	if face == "top":
		return TROUSERS
	if face == "bottom":
		return BOOTS
	return TROUSERS if y < 9 else BOOTS


def make_skin():
	rng = random.Random(7)
	img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	paint_box(img, rng, 0, 0, 8, 8, 8, head)
	paint_box(img, rng, 16, 16, 8, 12, 4, body)
	paint_box(img, rng, 40, 16, 4, 12, 4, arm)
	paint_box(img, rng, 32, 48, 4, 12, 4, arm)
	paint_box(img, rng, 0, 16, 4, 12, 4, leg)
	paint_box(img, rng, 16, 48, 4, 12, 4, leg)
	return img


def make_egg():
	rng = random.Random(11)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	cx, cy, rx, ry = 7.5, 8.0, 5.5, 7.0

	def inside(x, y, rx=rx, ry=ry):
		return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0

	spots = [(rng.randint(3, 12), rng.randint(3, 13)) for _ in range(9)]
	for y in range(16):
		for x in range(16):
			if not inside(x, y):
				continue
			if not inside(x, y, rx - 1.0, ry - 1.0):
				img.putpixel((x, y), (20, 60, 64, 255))
			elif any(abs(x - sx) <= 1 and abs(y - sy) <= 1 and (x + y + sx) % 2 == 0 for sx, sy in spots):
				img.putpixel((x, y), jitter(rng, SKIN, 3))
			else:
				img.putpixel((x, y), jitter(rng, TUNIC, 6))
	return img


if __name__ == "__main__":
	(OUT / "entity").mkdir(parents=True, exist_ok=True)
	(OUT / "item").mkdir(parents=True, exist_ok=True)
	make_skin().save(OUT / "entity" / "giantess.png")
	make_egg().save(OUT / "item" / "giantess_spawn_egg.png")
	print("textures written to", OUT)
