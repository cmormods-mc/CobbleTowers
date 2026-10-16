"""16x16 item sprites for the armor pieces, drawn natively at the size an inventory slot shows them.

Each piece is a left-half template (8 columns) mirrored across the middle, so every icon is symmetric and crisp. Letters:
O outline, L light, M base, S shade, T trim, D dark interior, G glow. Light comes from the top left, so the right half drops
one step. The set's look (`generate_armor_textures.Look`) supplies the colours; its pattern and tier add small accents.
"""

from __future__ import annotations

from PIL import Image

SIZE = 16

HALVES = {
    "helmet": [
        "......TT", "......TT", "....OOOO", "...OLLLL", "..OLLMMM", "..OLMMMM", "..OTTTTT", "..OMMMMM",
        "..OMDDDD", "..OMDGDD", "..OMDDDD", "..OMMO..", "..OOOO..", "........", "........", "........",
    ],
    "chestplate": [
        "........", ".OOOOO..", "OLLLMMOO", "OLLMMMMM", "OLMMMMMM", "OTTTTTTT", "OLMOMMMM", "OMMOMMMG",
        "OMMOMMMG", "OSSOMMMM", "OOOOMMMM", "....TTTT", "....MMMM", "....OOOO", "........", "........",
    ],
    "leggings": [
        "........", "..OTTTTT", "..OLMMMM", "..OLMMMM", "..OMMMMM", "..OLMMMO", "..OLMMMO", "..OLMMMO",
        "..OTTTTO", "..OMMMMO", "..OMMMMO", "..OMMMMO", "..OTTTTO", "..OOOOOO", "........", "........",
    ],
    "boots": [
        "........", "........", "........", "..OOOOO.", "..OTTTO.", "..OTTTO.", "..OLMMO.", "..OLMMO.",
        "..OMMMO.", "..OMMMO.", ".OLMMMO.", "OLLMMMO.", "OLMMMMO.", "OSSSSSO.", "OOOOOOO.", "........",
    ],
}


def _colors(look) -> dict:
    metal = look.cloth if look.s["pattern"] == "cloth" else look.metal
    return {"O": metal[0], "L": metal[4], "M": metal[3], "S": metal[2], "T": look.trim[3], "D": look.dark[1], "G": look.glow[4]}


def make_icon(l1, l2, slot: str, outline, interior, look) -> Image.Image:
    """The first four arguments are unused (the old sprites were cut from the layer art); the caller still passes them."""
    colors = _colors(look)
    full = []
    for half in HALVES[slot]:
        left = list(half)
        right = ["M" if ch == "L" else ch for ch in reversed(left)]
        # the cell just inside the right outline drops to shade, so the piece has a lit side and a shaded side
        for i, ch in enumerate(right):
            if ch in "LM" and i > 0 and right[i - 1] == "O":
                right[i] = "S"
        full.append(left + right)
    spec = look.s
    tier, pattern = spec["tier"], spec["pattern"]
    for y, row in enumerate(full):
        for x, ch in enumerate(row):
            if ch == "M" and pattern in ("mail", "scales") and (x + y * 2) % 5 == 0:
                row[x] = "L"
            elif ch == "M" and pattern == "planks" and x % 3 == 0:
                row[x] = "S"
    if slot == "helmet":
        for y in range(2 if tier == 1 else 1 if tier == 2 else 0):
            full[y] = ["."] * SIZE
        if spec["helm"] == "visor":
            for x in (5, 6, 9, 10):
                full[9][x] = "G"
        if spec["helm"] == "hood":
            full[6] = ["S" if ch == "T" else ch for ch in full[6]]
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            ch = full[y][x]
            if ch != ".":
                px[x, y] = (*colors[ch], 255)
    if slot == "chestplate" and spec["shoulder"] == "spike":
        for x in (1, 14):
            px[x, 0] = (*colors["T"], 255)
    return img
