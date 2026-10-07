"""Concept art for a Pixelated Byzantine card draft, built from real 16x16 tiles (one tessera = one pixel).

    python docs/design/concepts/byzantine/generate.py

Writes draft_mockup.png, card_states.png, card_closeup.png and tile_atlas.png next to this file. Nothing here ships in the mod: it is
a picture to react to. Sprites are the mod's own CC0 partner icons; the lettering is Minecraft's own font sheet (read from the local
client jar, not copied into the repository).
"""
import io
import math
import random
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SPRITES = ROOT / 'src/main/resources/assets/cobbletowers/textures/gui/partners'
CLIENT_JAR = Path.home() / '.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar'


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


GOLD_L, GOLD, GOLD_D, GOLD_S = map(rgb, ('#F6DC7A', '#DDAB3C', '#A8741E', '#6B4512'))
LAPIS = [rgb('#1E3FA0'), rgb('#2F5BD0'), rgb('#17307C')]
EMERALD = [rgb('#0F8F6A'), rgb('#19B488'), rgb('#0A5F48')]
RUBY = [rgb('#C4372C'), rgb('#E0583F'), rgb('#8E2420')]
AMETHYST = [rgb('#6A2F8A'), rgb('#8A4CB0'), rgb('#4A1F66')]
TEAL_WALL, TEAL_DARK, WALL_RED = rgb('#0F5B55'), rgb('#093C3A'), rgb('#7A2A24')
PARCH, PARCH_M, PARCH_D, CRACK = map(rgb, ('#E8D5A8', '#D2BE8E', '#A8935F', '#6B5638'))
INK, BROWN, CREAM, OUTLINE = map(rgb, ('#40291E', '#6B4A33', '#F0DFBF', '#211510'))
BURGUNDY, BURGUNDY_L = rgb('#773C38'), rgb('#9A524A')
GROUT = (26, 15, 10)
rng = random.Random(11)


# ---- canvas -------------------------------------------------------------------------------------------------------------

class Canvas:
    """RGB pixels plus a mask of which ones are mosaic tesserae (they get grout lines when the picture is enlarged)."""

    def __init__(self, w, h, color=(0, 0, 0)):
        self.w, self.h = w, h
        self.img = np.zeros((h, w, 3), np.uint8)
        self.img[:] = color
        self.mos = np.zeros((h, w), bool)

    def px(self, x, y, c, mos=False):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img[y, x] = c
            self.mos[y, x] = mos

    def fill(self, x0, y0, x1, y1, c, mos=False):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.img[y, x] = c
                self.mos[y, x] = mos

    def blit(self, rgba, x, y, mos=False):
        for yy in range(rgba.shape[0]):
            for xx in range(rgba.shape[1]):
                if rgba[yy, xx, 3] > 127:
                    self.px(x + xx, y + yy, tuple(int(v) for v in rgba[yy, xx, :3]), mos)

    def paste(self, other, x, y):
        for yy in range(other.h):
            for xx in range(other.w):
                if other.alpha[yy, xx]:
                    self.px(x + xx, y + yy, tuple(int(v) for v in other.img[yy, xx]), bool(other.mos[yy, xx]))


class Sprite(Canvas):
    """A canvas that remembers which pixels were drawn, so it can be pasted onto another."""

    def __init__(self, w, h):
        super().__init__(w, h)
        self.alpha = np.zeros((h, w), bool)

    def px(self, x, y, c, mos=False):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img[y, x] = c
            self.mos[y, x] = mos
            self.alpha[y, x] = True

    def fill(self, x0, y0, x1, y1, c, mos=False):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px(x, y, c, mos)

    def clear(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.alpha[y, x] = False


def render(canvas, scale, mosaic_grout=0.62, base_grout=0.9):
    """Enlarges with nearest sampling and cuts grout lines, deeper on the mosaic than on parchment, wood or text."""
    big = np.kron(canvas.img, np.ones((scale, scale, 1), np.uint8)).astype(np.float32)
    mos = np.kron(canvas.mos, np.ones((scale, scale), bool))
    ys, xs = np.mgrid[0:big.shape[0], 0:big.shape[1]]
    line = (xs % scale == 0) | (ys % scale == 0)
    top_light = (xs % scale == 1) & (ys % scale > 1) | (ys % scale == 1) & (xs % scale > 1)
    factor = np.where(line, np.where(mos, mosaic_grout, base_grout), 1.0)
    big *= factor[..., None]
    big[top_light & mos] = np.minimum(255, big[top_light & mos] * 1.10)
    return Image.fromarray(big.astype(np.uint8))


# ---- lettering: Minecraft's own font sheet -----------------------------------------------------------------------------

def load_font():
    with zipfile.ZipFile(CLIENT_JAR) as z:
        sheet = Image.open(io.BytesIO(z.read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')
    glyphs = {}
    for code in range(32, 127):
        cell = sheet.crop(((code % 16) * 8, (code // 16) * 8, (code % 16) * 8 + 8, (code // 16) * 8 + 8))
        a = np.array(cell)[:, :, 3] > 0
        cols = np.where(a.any(axis=0))[0]
        if len(cols) == 0:
            glyphs[chr(code)] = (a[:, :3], 3)
        else:
            glyphs[chr(code)] = (a[:, cols[0]:cols[-1] + 1], cols[-1] - cols[0] + 2)
    return glyphs


FONT = load_font()


def text_width(s):
    return sum(FONT.get(ch, FONT['?'])[1] for ch in s) - 1


def text(cv, x, y, s, color, shadow=None):
    for ch in s:
        g, adv = FONT.get(ch, FONT['?'])
        for yy in range(g.shape[0]):
            for xx in range(g.shape[1]):
                if g[yy, xx]:
                    if shadow:
                        cv.px(x + xx + 1, y + yy + 1, shadow)
                    cv.px(x + xx, y + yy, color)
        x += adv


def text_center(cv, cx, y, s, color, shadow=None):
    text(cv, cx - text_width(s) // 2, y, s, color, shadow)


def wrap(s, width):
    words, lines, cur = s.split(), [], ''
    for w in words:
        trial = (cur + ' ' + w).strip()
        if text_width(trial) <= width:
            cur = trial
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


# ---- the 16x16 tile set ----------------------------------------------------------------------------------------------

def new_tile():
    return Sprite(16, 16)


def tile_field(shades, speck=None, seed=1):
    r = random.Random(seed)
    t = new_tile()
    grid = [[0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            roll = r.random()
            if y and roll < 0.42:
                grid[y][x] = grid[y - 1][x]
            elif x and roll < 0.72:
                grid[y][x] = grid[y][x - 1]
            else:
                grid[y][x] = r.choice([0, 0, 1, 2])
            c = shades[grid[y][x]]
            if speck and r.random() < 0.05:
                c = speck
            t.px(x, y, c, True)
    return t


def tile_parchment(seed=3):
    r = random.Random(seed)
    t = new_tile()
    for y in range(16):
        for x in range(16):
            roll = r.random()
            t.px(x, y, PARCH if roll < 0.7 else PARCH_M if roll < 0.95 else PARCH_D)
    for _ in range(2):
        x, y = r.randrange(2, 14), r.randrange(2, 14)
        for i in range(r.randrange(3, 6)):
            t.px(x + i, y + (i % 2), CRACK)
    return t


def tile_roll():
    """A horizontal scroll roll, eight pixels tall, that repeats along its length."""
    t = new_tile()
    rows = [OUTLINE, PARCH_D, PARCH_M, PARCH, PARCH, PARCH_M, PARCH_D, OUTLINE]
    for y, c in enumerate(rows):
        for x in range(16):
            t.px(x, y, c)
    for x in (3, 4, 11):
        t.px(x, 3, PARCH_M)
    return t


def tile_band():
    """The ornament band (eight rows): gold edges, a lapis ground, gold lozenges with a ruby heart."""
    t = new_tile()
    edge = [GOLD_D, GOLD_L]
    for x in range(16):
        t.px(x, 0, edge[0], True)
        t.px(x, 1, edge[1], True)
        t.px(x, 6, edge[1], True)
        t.px(x, 7, edge[0], True)
    pattern = [
        '..G.......G.....',
        '.GRG.....GRG....',
        '..G.......G.....',
        '................',
    ]
    for y in range(4):
        for x in range(16):
            ch = pattern[y][x]
            t.px(x, 2 + y, GOLD if ch == 'G' else RUBY[0] if ch == 'R' else LAPIS[(x + y) % 3 if (x * 7 + y * 3) % 5 == 0 else 0], True)
    return t


def rotate(tile, k):
    out = new_tile()
    img, a, m = np.rot90(tile.img, k), np.rot90(tile.alpha, k), np.rot90(tile.mos, k)
    out.img, out.alpha, out.mos = img.copy(), a.copy(), m.copy()
    return out


def band_strip(length, side):
    """The ornament band as an RGB strip along a card or screen edge: top, bottom, left or right. Thickness is eight pixels."""
    band = tile_band()
    top = np.stack([band.img[0:8, x % 16] for x in range(length)], axis=1)          # (8, length, 3)
    if side == 'top':
        return top
    if side == 'bottom':
        return top[::-1]
    left = np.transpose(top, (1, 0, 2))                                              # (length, 8, 3): outer gold on the left
    return left if side == 'left' else left[:, ::-1]


def put_rgb(cv, arr, x, y, mos=True):
    for yy in range(arr.shape[0]):
        for xx in range(arr.shape[1]):
            cv.px(x + xx, y + yy, tuple(int(v) for v in arr[yy, xx]), mos)


def transposed(tile):
    out = new_tile()
    out.img, out.alpha, out.mos = np.transpose(tile.img, (1, 0, 2)).copy(), tile.alpha.T.copy(), tile.mos.T.copy()
    return out


def tile_corner():
    t = new_tile()
    t.fill(0, 0, 8, 8, GOLD, True)
    t.fill(0, 0, 8, 1, GOLD_D, True)
    t.fill(0, 0, 1, 8, GOLD_D, True)
    t.fill(1, 1, 7, 2, GOLD_L, True)
    t.fill(1, 1, 2, 7, GOLD_L, True)
    t.fill(3, 3, 6, 6, RUBY[0], True)
    t.px(3, 3, RUBY[1], True)
    t.fill(0, 7, 8, 8, GOLD_D, True)
    t.fill(7, 0, 8, 8, GOLD_D, True)
    return t


def tile_wall(seed=5):
    """The wall behind everything: teal with gold-outlined ruby lozenges, after the reference's patterned cloth."""
    r = random.Random(seed)
    t = new_tile()
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d <= 3:
                c = WALL_RED if (x + y) % 5 else rgb('#A33A30')
            elif d <= 4.5:
                c = GOLD
            elif d <= 6.5:
                c = TEAL_WALL if r.random() < 0.8 else TEAL_DARK
            elif d <= 7.5:
                c = GOLD_D
            else:
                c = TEAL_DARK if r.random() < 0.7 else TEAL_WALL
            t.px(x, y, c, True)
    return t


# ---- the card ----------------------------------------------------------------------------------------------------------

THEMES = {
    'rain': dict(field=LAPIS, speck=rgb('#1F8F9E')),
    'glass': dict(field=AMETHYST, speck=GOLD),
    'bars': dict(field=EMERALD, speck=GOLD_L),
    'reward': dict(field=RUBY, speck=GOLD),
}

CARD_W, CARD_H = 80, 128


def draw_halo(cv, cx, cy, r):
    """A gold nimbus: dark rim, a ring of gold leaves, a dark inner ring, a two-tone gold disc."""
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > r:
                continue
            ang = math.degrees(math.atan2(y - cy, x - cx)) % 360
            if d > r - 1.2:
                c = GOLD_S
            elif d > r - 5:
                leaf = int(ang // 15) % 2
                gap = (ang % 15) < 2.5
                c = GOLD_D if gap else (GOLD_L if leaf else GOLD)
            elif d > r - 6.2:
                c = GOLD_D
            else:
                c = GOLD_L if ((x // 2 + y // 2) % 3 == 0 or d < r * 0.35) else GOLD
                if x < cx - r * 0.45 and y < cy:
                    c = GOLD_L
            cv.px(x, y, c, True)
    # the cruciform nimbus: three arms (the figure covers the fourth), ruby inlay, flared at the rim
    for dx, dy in ((0, -1), (-1, 0), (1, 0)):
        for step in range(6, r - 3):
            flare = 1 if step >= r - 8 else 0
            for w in range(-2 - flare, 2 + flare):
                x = cx + dx * step + (dy != 0) * w
                y = cy + dy * step + (dx != 0) * w
                inner = -1 <= w <= 0
                cv.px(x, y, RUBY[1] if inner and step % 3 == 0 else RUBY[0] if inner else GOLD_D, True)
        tip = r - 4
        cv.px(cx + dx * tip, cy + dy * tip, GOLD_L, True)


def make_card(name, risk, theme, species, selected=False, hover=False, votes=0):
    t = THEMES[theme]
    cv = Sprite(CARD_W + 8, CARD_H + 8)
    ox, oy = 4, 4
    # parchment backing with torn side edges
    parch = [tile_parchment(s) for s in (3, 4, 5, 6)]
    for ty in range(CARD_H // 16):
        for tx in range(CARD_W // 16):
            cv.paste(parch[(tx + ty * 2) % 4], ox + tx * 16, oy + ty * 16)
    r = random.Random(hash(name) & 0xffff)
    for y in range(CARD_H):
        for side in (0, 1):
            cut = 0 if y < 8 or y > CARD_H - 9 else (1 if r.random() < 0.55 else 0) + (1 if r.random() < 0.22 else 0)
            for k in range(cut):
                cv.clear(ox + k if side == 0 else ox + CARD_W - 1 - k, oy + y)
    # rolls top and bottom, with knobs
    roll = tile_roll()
    for tx in range(CARD_W // 16):
        cv.paste(roll, ox + tx * 16, oy)
        cv.paste(roll, ox + tx * 16, oy + CARD_H - 8)
    for ry in (oy, oy + CARD_H - 8):
        cv.fill(ox - 2, ry + 1, ox + 1, ry + 7, PARCH_D)
        cv.fill(ox + CARD_W - 1, ry + 1, ox + CARD_W + 2, ry + 7, PARCH_D)
        cv.fill(ox - 3, ry + 2, ox - 2, ry + 6, OUTLINE)
        cv.fill(ox + CARD_W + 2, ry + 2, ox + CARD_W + 3, ry + 6, OUTLINE)
    # mosaic panel: band frame
    px0, py0 = ox + 6, oy + 9
    pw, ph = CARD_W - 12, CARD_H - 18
    put_rgb(cv, band_strip(pw - 16, 'top'), px0 + 8, py0)
    put_rgb(cv, band_strip(pw - 16, 'bottom'), px0 + 8, py0 + ph - 8)
    put_rgb(cv, band_strip(ph - 16, 'left'), px0, py0 + 8)
    put_rgb(cv, band_strip(ph - 16, 'right'), px0 + pw - 8, py0 + 8)
    corner = tile_corner()
    for cx_, cy_, k in ((px0, py0, 0), (px0 + pw - 8, py0, 3), (px0, py0 + ph - 8, 1), (px0 + pw - 8, py0 + ph - 8, 2)):
        put_rgb(cv, np.rot90(corner.img[0:8, 0:8], k), cx_, cy_)
    # the field
    fx0, fy0, fw, fh = px0 + 8, py0 + 8, pw - 16, ph - 16
    tiles = [tile_field(t['field'], t['speck'], s) for s in (1, 2, 3, 4)]
    for y in range(fh):
        for x in range(fw):
            tt = tiles[((x // 16) + (y // 16) * 3) % 4]
            cv.px(fx0 + x, fy0 + y, tuple(int(v) for v in tt.img[y % 16, x % 16]), True)
    # halo and figure
    cx, cy = fx0 + fw // 2, fy0 + 36
    draw_halo(cv, cx, cy, 23)
    sprite = Image.open(SPRITES / f'{species}.png').convert('RGBA')
    sp = np.array(sprite)
    cv.blit(sp, cx - 24, cy - 16 + 8)
    # robe: a green mosaic hill that the figure stands on
    for x in range(fw):
        top = fy0 + fh - 22 - int(3 * math.sin(x / 6.5 + 1)) + (2 if hash(name) % 2 else 0)
        for y in range(top, fy0 + fh):
            shade = EMERALD[1] if (x + y) % 5 == 0 else EMERALD[0] if (x * 3 + y) % 4 else EMERALD[2]
            if y == top:
                shade = GOLD
            elif y == top + 1:
                shade = GOLD_D
            cv.px(fx0 + x, y, shade, True)
    # a gold cross-mark per level of risk, top right of the field
    marks = {0: 1, 1: 2, 2: 3}.get(risk, 0)
    for i in range(marks):
        mx, my = fx0 + fw - 7 - i * 6, fy0 + 3
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
            cv.px(mx + dx, my + dy, GOLD_L if (dx, dy) == (0, 0) else GOLD, True)
    # votes as small gold tesserae on the top roll
    for v in range(votes):
        cv.fill(ox + 8 + v * 5, oy + 2, ox + 11 + v * 5, oy + 5, GOLD_L)
        cv.fill(ox + 8 + v * 5, oy + 4, ox + 11 + v * 5, oy + 5, GOLD_D)
    # nameplate: a parchment tablet laid over the bottom of the field
    plate_w, plate_h = CARD_W - 6, 20
    plx, ply = ox + 3, fy0 + fh - plate_h + 5
    cv.fill(plx - 1, ply - 1, plx + plate_w + 1, ply + plate_h + 1, OUTLINE)
    cv.fill(plx, ply, plx + plate_w, ply + plate_h, PARCH)
    cv.fill(plx, ply, plx + plate_w, ply + 1, PARCH_D)
    cv.fill(plx, ply + plate_h - 1, plx + plate_w, ply + plate_h, PARCH_D)
    cv.fill(plx + 2, ply + 2, plx + 4, ply + 4, GOLD_D)
    cv.fill(plx + plate_w - 4, ply + 2, plx + plate_w - 2, ply + 4, GOLD_D)
    text_center(cv, plx + plate_w // 2, ply + 3, name, INK)
    text_center(cv, plx + plate_w // 2, ply + 11, {0: 'Minor', 1: 'Moderate', 2: 'Severe'}[risk], BROWN)
    # selection and hover: gold tesserae set around the whole card
    ring = 2 if selected else 1 if hover else 0
    if ring:
        out = Sprite(cv.w + ring * 2, cv.h + ring * 2)
        out.paste(cv, ring, ring)
        for y in range(out.h):
            for x in range(out.w):
                if out.alpha[y, x]:
                    continue
                for k in range(1, ring + 1):
                    if any(0 <= x + dx < out.w and 0 <= y + dy < out.h and out.alpha[y + dy, x + dx]
                           for dx in range(-k, k + 1) for dy in range(-k, k + 1)):
                        out.px(x, y, GOLD_L if k == ring else GOLD_D, True)
                        break
        return out
    return cv


def unroll(card, u):
    """The reveal: the card starts as a closed roll of parchment and opens from its middle outwards."""
    w, h = card.w, card.h
    out = Sprite(w, h)
    visible = max(0, int(w * u))
    cx = w // 2
    x0, x1 = cx - visible // 2, cx + visible // 2
    for y in range(h):
        for x in range(x0, x1):
            if 0 <= x < w and card.alpha[y, x]:
                out.px(x, y, tuple(int(v) for v in card.img[y, x]), bool(card.mos[y, x]))
    shades = [OUTLINE, PARCH_D, PARCH_M, PARCH, PARCH, PARCH_M, PARCH_D, OUTLINE]
    for edge in ((x0 - 8, True), (x1, False)) if visible > 6 else ((cx - 8, True), (cx, False)):
        ex, left = edge
        for i, c in enumerate(shades):
            xx = ex + (i if left else i)
            for y in range(6, h - 6):
                out.px(xx, y, c)
        for y in range(4, h - 4):
            if y in (4, 5, h - 6, h - 5):
                for i in range(8):
                    out.px(ex + i, y, PARCH_D)
    if visible <= 6:
        for y in range(6, h - 6, 11):
            out.fill(cx - 8, y, cx + 8, y + 1, CRACK)
    return out


# ---- scenes ------------------------------------------------------------------------------------------------------------

CARDS = [
    ('Downpour', 1, 'rain', 'politoed'),
    ('Glass Cannon', 2, 'glass', 'gallade'),
    ('Locked Doors', 1, 'bars', 'registeel'),
    ('Windfall', 0, 'reward', 'persian'),
]


def burgundy_button(cv, x, y, w, h, label, active=True):
    base = BURGUNDY if active else rgb('#5C4A40')
    cv.fill(x, y, x + w, y + h, OUTLINE)
    cv.fill(x + 1, y + 1, x + w - 1, y + h - 1, GOLD_D if active else rgb('#7A6A55'), True)
    cv.fill(x + 2, y + 2, x + w - 2, y + h - 2, base, True)
    cv.fill(x + 2, y + 2, x + w - 2, y + 3, BURGUNDY_L if active else rgb('#6E5C50'), True)
    for sx, sy in ((x + 3, y + 3), (x + w - 5, y + 3), (x + 3, y + h - 5), (x + w - 5, y + h - 5)):
        cv.fill(sx, sy, sx + 2, sy + 2, GOLD, True)
    text_center(cv, x + w // 2, y + h // 2 - 4, label, CREAM if active else rgb('#A89A86'), OUTLINE if active else None)


def scroll_panel(cv, x, y, w, h):
    """A vertical parchment scroll for the inspection text."""
    cv_tiles = [tile_parchment(s) for s in (7, 8, 9, 10)]
    for yy in range(0, h, 16):
        for xx in range(0, w, 16):
            t = cv_tiles[(xx // 16 + yy // 16) % 4]
            for ty in range(min(16, h - yy)):
                for tx in range(min(16, w - xx)):
                    cv.px(x + xx + tx, y + yy + ty, tuple(int(v) for v in t.img[ty, tx]))
    roll = tile_roll()
    for xx in range(0, w, 16):
        cv.blit(np.dstack([roll.img[:, :min(16, w - xx)], np.where(roll.alpha[:, :min(16, w - xx)], 255, 0).astype(np.uint8)[..., None]]).astype(np.uint8), x + xx, y)
        cv.blit(np.dstack([roll.img[:, :min(16, w - xx)], np.where(roll.alpha[:, :min(16, w - xx)], 255, 0).astype(np.uint8)[..., None]]).astype(np.uint8), x + xx, y + h - 8)
    cv.fill(x - 1, y, x, y + h, OUTLINE)
    cv.fill(x + w, y, x + w + 1, y + h, OUTLINE)
    # a mosaic band across the top of the writing
    band = tile_band()
    for xx in range(w - 8):
        col = np.dstack([band.img[0:8, xx % 16:xx % 16 + 1], np.full((8, 1, 1), 255, np.uint8)])
        cv.blit(col, x + 4 + xx, y + 10, True)


def mockup():
    W, H = 427, 240
    cv = Canvas(W, H)
    wall = tile_wall()
    for ty in range(0, H, 16):
        for tx in range(0, W, 16):
            cv.blit(wall.img.__class__ and np.dstack([wall.img, np.full((16, 16, 1), 255, np.uint8)]), tx, ty, True)
    cv.img[:] = (cv.img * 0.42).astype(np.uint8)       # the wall recedes; the cards carry the colour
    # screen frame: the ornament band round the edge
    put_rgb(cv, band_strip(W, 'top'), 0, 0)
    put_rgb(cv, band_strip(W, 'bottom'), 0, H - 8)
    put_rgb(cv, band_strip(H - 16, 'left'), 0, 8)
    put_rgb(cv, band_strip(H - 16, 'right'), W - 8, 8)
    cv.fill(8, 8, W - 8, 9, GOLD_S)
    text_center(cv, W // 2, 12, 'Floor 4 cleared', CREAM, OUTLINE)
    text_center(cv, W // 2, 22, 'Choose a modifier for the next floor', GOLD_L, OUTLINE)

    cards = [make_card(*CARDS[0], votes=1), make_card(*CARDS[1], selected=True), make_card(*CARDS[2], hover=True), None]
    cards[3] = None
    xs = [10, 106, 202]
    for i, card in enumerate(cards[:3]):
        raise_by = 3 if i == 1 else 0
        cv.paste(card, xs[i] - (card.w - (CARD_W + 8)) // 2, 36 - raise_by - (card.h - (CARD_H + 8)) // 2)

    # the inspection scroll
    sx, sy, sw, sh = 298, 36, 120, 128
    scroll_panel(cv, sx, sy, sw, sh)
    text(cv, sx + 6, sy + 22, 'Glass Cannon', INK)
    text(cv, sx + 6, sy + 31, 'Risk: severe', BROWN)
    y = sy + 43
    for line in ['Start battles at 60% HP with +2 Attack.', 'Boss health: 120% of baseline.', 'Eligible reward amounts: x1.25.']:
        for part in wrap(line, sw - 12):
            text(cv, sx + 6, y, part, INK)
            y += 9
        y += 2
    text(cv, sx + sw - 30, sy + sh - 20, '1/4', BROWN)

    # actions
    burgundy_button(cv, 14, 176, 180, 18, 'Confirm')
    burgundy_button(cv, 200, 176, 98, 18, 'Close')
    burgundy_button(cv, 14, 198, 90, 18, 'Vendor')
    burgundy_button(cv, 110, 198, 90, 18, 'Ready', active=False)
    burgundy_button(cv, 206, 198, 92, 18, 'Cash out')
    text(cv, 306, 180, 'Alex ready', rgb('#A9B781'), OUTLINE)
    text(cv, 306, 190, 'Sam waiting', rgb('#C6AC87'), OUTLINE)
    text(cv, 306, 200, 'Jo waiting', rgb('#C6AC87'), OUTLINE)
    return cv


def states():
    cards = [make_card(*CARDS[1])]
    base = cards[0]
    labels = ['Closed', 'Unrolling', 'Open', 'Hover', 'Selected']
    variants = [unroll(base, 0.0), unroll(base, 0.45), base, make_card(*CARDS[1], hover=True), make_card(*CARDS[1], selected=True, votes=2)]
    pad = 8
    cell_w = max(v.w for v in variants) + pad
    W = cell_w * len(variants) + pad
    H = max(v.h for v in variants) + 34
    cv = Canvas(W, H, TEAL_DARK)
    wall = tile_wall()
    for ty in range(0, H, 16):
        for tx in range(0, W, 16):
            cv.blit(np.dstack([wall.img, np.full((16, 16, 1), 255, np.uint8)]), tx, ty, True)
    cv.img[:] = (cv.img * 0.42).astype(np.uint8)
    for i, v in enumerate(variants):
        x = pad + i * cell_w + (cell_w - pad - v.w) // 2
        cv.paste(v, x, 8 + (max(q.h for q in variants) - v.h) // 2)
        text_center(cv, pad + i * cell_w + (cell_w - pad) // 2, H - 18, labels[i], CREAM, OUTLINE)
    return cv


def atlas():
    items = [('wall', tile_wall()), ('lapis', tile_field(LAPIS, rgb('#1F8F9E'), 1)), ('emerald', tile_field(EMERALD, GOLD_L, 2)),
             ('ruby', tile_field(RUBY, GOLD, 3)), ('amethyst', tile_field(AMETHYST, GOLD, 4)), ('band', tile_band()),
             ('side', transposed(tile_band())), ('corner', tile_corner()), ('roll', tile_roll()), ('parchment', tile_parchment())]
    cols = 5
    cell = 16 + 30
    W = cols * cell + 8
    H = ((len(items) + cols - 1) // cols) * (cell + 8) + 8
    cv = Canvas(W, H, rgb('#1B120D'))
    for i, (name, t) in enumerate(items):
        x = 8 + (i % cols) * cell
        y = 8 + (i // cols) * (cell + 8)
        cv.fill(x - 1, y - 1, x + 17, y + 17, GOLD_S)
        cv.fill(x, y, x + 16, y + 16, rgb('#2A1C15'))
        cv.paste(t, x, y)
        text_center(cv, x + 8, y + 20, name, CREAM)
    return cv


if __name__ == '__main__':
    render(mockup(), 4).save(HERE / 'draft_mockup.png')
    render(states(), 4).save(HERE / 'card_states.png')
    closeup = Canvas(CARD_W + 28, CARD_H + 28, rgb('#1B120D'))
    c = make_card(*CARDS[0], selected=True, votes=1)
    closeup.paste(c, 8, 8)
    render(closeup, 8).save(HERE / 'card_closeup.png')
    render(atlas(), 8).save(HERE / 'tile_atlas.png')
    print('wrote', [p.name for p in HERE.glob('*.png')])
