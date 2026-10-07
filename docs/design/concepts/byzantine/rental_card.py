"""Concept art for the Rental Draft card: a Byzantine mosaic top half with the sprite in a nimbus, a mosaic border whose metal and
jewels say the rarity, and a clean parchment lower half with a faceted gem for each move, coloured by the move's type.

    python docs/design/concepts/byzantine/rental_card.py

Writes rental_pack_mockup.png, rental_card_closeup.png, rental_rarity_ladder.png and gem_legend.png next to this file. A card is
112x160 pixels: seven by ten 16x16 tiles, one tessera per pixel. Real data from the shipped rental sets.
"""
import io
import math
import random
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import generate as g  # noqa: E402
from generate import Canvas, Sprite, render, text, text_center, text_width, rgb  # noqa: E402

CW, CH = 112, 160
CARDS_JAR = Path('L:/claude-cobbleraids-work/testserver-full/mods-disabled-for-testing/cobblemon-cards-fabric-1.0.4.jar')

SIL = [rgb('#EEF1F5'), rgb('#BFC6CF'), rgb('#7C8591'), rgb('#454B55')]
BRZ = [rgb('#D49A58'), rgb('#A86F35'), rgb('#6B4320'), rgb('#3E2610')]
GLD = [g.GOLD_L, g.GOLD, g.GOLD_D, g.GOLD_S]

# a gemstone for each of the eighteen types
TYPE_COLORS = {
    'normal': '#C9BFA8', 'fire': '#E5502B', 'water': '#2F70E3', 'electric': '#F4CB3C', 'grass': '#2FAA52', 'ice': '#7ADCE8',
    'fighting': '#B43A2C', 'poison': '#9C4EC6', 'ground': '#C6923F', 'flying': '#93A8F2', 'psychic': '#EA5C9E', 'bug': '#9EC52F',
    'rock': '#A88D5C', 'ghost': '#6C50A4', 'dragon': '#4D41D6', 'dark': '#4C4048', 'steel': '#8CA2B8', 'fairy': '#F4A3D2',
}
MOVE_TYPES = {
    'flareblitz': 'fire', 'extremespeed': 'normal', 'closecombat': 'fighting', 'wildcharge': 'electric', 'flamethrower': 'fire',
    'airslash': 'flying', 'focusblast': 'fighting', 'roost': 'flying', 'psychic': 'psychic', 'shadowball': 'ghost',
    'energyball': 'grass', 'scald': 'water', 'icebeam': 'ice', 'rapidspin': 'normal', 'darkpulse': 'dark', 'dragonclaw': 'dragon',
    'earthquake': 'ground', 'firepunch': 'fire', 'ironhead': 'steel', 'rockslide': 'rock', 'poisonjab': 'poison',
    'dragonpulse': 'dragon', 'thunderbolt': 'electric', 'outrage': 'dragon', 'stoneedge': 'rock', 'firefang': 'fire',
    'originpulse': 'water', 'thunder': 'electric', 'hurricane': 'flying', 'earthpower': 'ground',
}
PHYSICAL = {'flareblitz', 'extremespeed', 'closecombat', 'wildcharge', 'firepunch', 'ironhead', 'rockslide', 'poisonjab', 'dragonclaw',
            'earthquake', 'outrage', 'stoneedge', 'firefang', 'rapidspin'}
STATUS = {'roost'}


def category(move):
    return 'physical' if move in PHYSICAL else 'status' if move in STATUS else 'special'


MOVE_NAMES = {'flareblitz': 'Flare Blitz', 'extremespeed': 'Extreme Speed', 'closecombat': 'Close Combat', 'wildcharge': 'Wild Charge',
              'icebeam': 'Ice Beam', 'rapidspin': 'Rapid Spin', 'darkpulse': 'Dark Pulse', 'dragonclaw': 'Dragon Claw',
              'earthquake': 'Earthquake', 'firepunch': 'Fire Punch', 'originpulse': 'Origin Pulse', 'earthpower': 'Earth Power',
              'dragonpulse': 'Dragon Pulse', 'stoneedge': 'Stone Edge', 'firefang': 'Fire Fang', 'shadowball': 'Shadow Ball',
              'poisonjab': 'Poison Jab', 'rockslide': 'Rock Slide', 'ironhead': 'Iron Head', 'airslash': 'Air Slash',
              'focusblast': 'Focus Blast', 'energyball': 'Energy Ball', 'thunderbolt': 'Thunderbolt', 'thunder': 'Thunder'}

# what each rarity is made of: the border metal, the band's ground, the mosaic field, and how grand the nimbus is
RARITY = {
    'common': dict(metal=BRZ, ground=[rgb('#2B3550'), rgb('#36436A'), rgb('#222B42')], motif=BRZ[0], gem=rgb('#7A3A2A'),
                   field=[rgb('#2A3F7A'), rgb('#37509A'), rgb('#1E2E5C')], speck=None, nimbus='plain', rank=0),
    'uncommon': dict(metal=SIL, ground=[rgb('#0E4A3A'), rgb('#146050'), rgb('#0A3A2E')], motif=SIL[0], gem=rgb('#2F70E3'),
                     field=[rgb('#0F8F6A'), rgb('#19B488'), rgb('#0A5F48')], speck=SIL[0], nimbus='leaf', rank=1),
    'rare': dict(metal=GLD, ground=g.LAPIS, motif=g.RUBY[0], gem=g.RUBY[0],
                 field=[rgb('#1E3FA0'), rgb('#2F5BD0'), rgb('#17307C')], speck=rgb('#1F8F9E'), nimbus='leaf', rank=2),
    'epic': dict(metal=GLD, ground=g.AMETHYST, motif=g.GOLD_L, gem=rgb('#2F70E3'),
                 field=g.AMETHYST, speck=g.GOLD, nimbus='beaded', rank=3),
    'mythic': dict(metal=GLD, ground=[rgb('#0B6B63'), rgb('#12968B'), rgb('#084A45')], motif=g.GOLD_L, gem=rgb('#F4F0E6'),
                   field=[rgb('#14243F'), rgb('#223A66'), rgb('#0C1630')], speck=rgb('#8FE3F0'), nimbus='radiant', rank=5),
    'legendary': dict(metal=GLD, ground=[rgb('#8E2420'), rgb('#B8342A'), rgb('#6E1A18')], motif=g.GOLD_L, gem=rgb('#F4F0E6'),
                      field=[rgb('#0F1E56'), rgb('#17307C'), rgb('#0A1440')], speck=g.GOLD_L, nimbus='radiant', rank=4),
}


def mix(c, o, t):
    return tuple(int(c[i] + (o[i] - c[i]) * t) for i in range(3))


def load_sprite(species):
    for folder in (g.SPRITES,):
        p = folder / f'{species}.png'
        if p.exists():
            return np.array(Image.open(p).convert('RGBA'))
    with zipfile.ZipFile(CARDS_JAR) as z:
        for name in z.namelist():
            if name.endswith(f'/{species}.png') and '/entity_icon/' in name:
                return np.array(Image.open(io.BytesIO(z.read(name))).convert('RGBA'))
        for suffix in ('_male', '_female'):
            for name in z.namelist():
                if name.endswith(f'/{species}{suffix}.png') and '/entity_icon/' in name:
                    return np.array(Image.open(io.BytesIO(z.read(name))).convert('RGBA'))
    raise FileNotFoundError(species)


# ---- the border band, in any metal ----------------------------------------------------------------------------------------

def band_tile(metal, ground, motif, gem):
    t = g.new_tile()
    for x in range(16):
        t.px(x, 0, metal[2], True)
        t.px(x, 1, metal[0], True)
        t.px(x, 6, metal[0], True)
        t.px(x, 7, metal[2], True)
    pattern = ['..G.......G.....', '.GRG.....GRG....', '..G.......G.....', '................']
    for y in range(4):
        for x in range(16):
            ch = pattern[y][x]
            fill = metal[1] if ch == 'G' else gem if ch == 'R' else ground[1 if (x * 7 + y * 3) % 5 == 0 else 0]
            t.px(x, 2 + y, fill, True)
    return t


def strip(tile, length, side):
    top = np.stack([tile.img[0:8, x % 16] for x in range(length)], axis=1)
    if side == 'top':
        return top
    if side == 'bottom':
        return top[::-1]
    left = np.transpose(top, (1, 0, 2))
    return left if side == 'left' else left[:, ::-1]


def corner(metal, gem):
    c = np.zeros((8, 8, 3), np.uint8)
    for y in range(8):
        for x in range(8):
            c[y, x] = metal[1]
    c[0, :] = metal[2]
    c[:, 0] = metal[2]
    c[1, 1:7] = metal[0]
    c[1:7, 1] = metal[0]
    c[7, :] = metal[2]
    c[:, 7] = metal[2]
    c[3:6, 3:6] = gem
    c[3, 3] = mix(gem, (255, 255, 255), 0.5)
    return c


# ---- the nimbus --------------------------------------------------------------------------------------------------------------

def nimbus(cv, cx, cy, r, metal, style):
    light, mid, dark, shadow = metal
    for y in range(cy - r - 6, cy + r + 7):
        for x in range(cx - r - 6, cx + r + 7):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            ang = math.degrees(math.atan2(y - cy, x - cx)) % 360
            if style == 'radiant' and r < d <= r + 6:
                ray = abs(((ang + 11.25) % 22.5) - 11.25)
                if ray < (r + 6 - d) * 0.55:
                    cv.px(x, y, light if int(ang // 22.5) % 2 == 0 else mid, True)
                continue
            if d > r:
                continue
            if d > r - 1.2:
                c = shadow
            elif style == 'plain':
                c = light if d > r - 3 else mid if (x // 2 + y // 2) % 4 else dark
            elif d > r - 5 or False:
                gap = (ang % 15) < 2.5
                c = dark if gap else (light if int(ang // 15) % 2 else mid)
            elif d > r - 6.2:
                c = dark
            else:
                c = mid if (x // 2 + y // 2) % 5 else dark
                if x < cx - r * 0.35 and y < cy - r * 0.2:
                    c = light if (x // 2 + y // 2) % 2 else mid
            cv.px(x, y, c, True)
    if style == 'beaded':
        for k in range(18):
            a = math.radians(k * 20 + 10)
            bx, by = int(round(cx + (r + 3) * math.cos(a))), int(round(cy + (r + 3) * math.sin(a)))
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                cv.px(bx + dx, by + dy, light if (dx, dy) == (0, 0) else mid, True)


# ---- gems ---------------------------------------------------------------------------------------------------------------------

SHAPES = {
    'special': (['..OOO..', '.OBBBO.', 'OBTTBBO', 'OBTTBDO', 'OBBBDDO', '.ODDDO.', '..OOO..'], (2, 2)),
    'physical': (['.OOOOO.', 'OBBBBBO', 'OBTTTBO', 'OBTTTDO', 'OBBBDDO', 'ODDDDDO', '.OOOOO.'], (2, 2)),
    'status': (['...O...', '..OBO..', '.OBTBO.', 'OBTTBDO', '.OBDDO.', '..ODO..', '...O...'], (3, 2)),
}


def gem(cv, x, y, hex_color, shape='special'):
    """A cut gem: round for a special move, square-cut for a physical one, a lozenge for a status move."""
    base = rgb(hex_color)
    light, dark, edge = mix(base, (255, 255, 255), 0.5), mix(base, (0, 0, 0), 0.38), mix(base, (0, 0, 0), 0.68)
    rows, spark = SHAPES[shape]
    for yy, row in enumerate(rows):
        for xx, ch in enumerate(row):
            if ch == '.':
                continue
            cv.px(x + xx, y + yy, {'O': edge, 'B': base, 'T': light, 'D': dark}[ch], True)
    cv.px(x + spark[0], y + spark[1], (255, 255, 255), True)


def star(cv, x, y, color):
    for dx, dy in ((2, 0), (1, 1), (2, 1), (3, 1), (0, 2), (1, 2), (2, 2), (3, 2), (4, 2), (1, 3), (2, 3), (3, 3), (2, 4)):
        cv.px(x + dx, y + dy, color)


def orb(cv, x, y, color):
    for dx, dy in ((1, 0), (2, 0), (3, 0), (0, 1), (1, 1), (2, 1), (3, 1), (4, 1), (0, 2), (1, 2), (2, 2), (3, 2), (4, 2),
                   (0, 3), (1, 3), (2, 3), (3, 3), (4, 3), (1, 4), (2, 4), (3, 4)):
        cv.px(x + dx, y + dy, color)
    cv.px(x + 1, y + 1, mix(color, (255, 255, 255), 0.5))


# ---- the card ----------------------------------------------------------------------------------------------------------------

def pretty(s):
    table = {'roughskin': 'Rough Skin', 'magicguard': 'Magic Guard', 'sandrush': 'Sand Rush'}
    return table.get(s, s.replace('_', ' ').title())


PLATE = dict(x=2, y=60, w=CW - 4, h=21)      # the nameplate; the field's window ends where it begins
FIELD = dict(x=8, y=8, w=CW - 16, h=62)       # the mosaic field, part of which the nameplate covers
TEXT = dict(ability_y=86, item_y=95, rule_y=107, move_y=113, move_step=10, icon_x=14, text_x=22, gem_text_x=25)


def border_layer(cv, spec):
    metal = spec['metal']
    band = band_tile(metal, spec['ground'], spec['motif'], spec['gem'])
    g.put_rgb(cv, strip(band, CW - 16, 'top'), 8, 0)
    g.put_rgb(cv, strip(band, CW - 16, 'bottom'), 8, CH - 8)
    g.put_rgb(cv, strip(band, CH - 16, 'left'), 0, 8)
    g.put_rgb(cv, strip(band, CH - 16, 'right'), CW - 8, 8)
    for cx_, cy_, k in ((0, 0, 0), (CW - 8, 0, 3), (0, CH - 8, 1), (CW - 8, CH - 8, 2)):
        g.put_rgb(cv, np.rot90(corner(metal, spec['gem']), k), cx_, cy_)


def frame_layer(rarity):
    """Everything about the card that does not change: border, flat cream description area, nameplate, the little ability and item
    icons and the rule between them and the moves. The field's window is left clear."""
    spec = RARITY[rarity]
    metal = spec['metal']
    cv = Sprite(CW, CH)
    for y in range(CH):
        for x in range(CW):
            cv.px(x, y, g.PARCH, False)
    border_layer(cv, spec)
    px0, py0, pw, ph = PLATE['x'], PLATE['y'], PLATE['w'], PLATE['h']
    cv.fill(px0 - 1, py0 - 1, px0 + pw + 1, py0 + ph + 1, g.OUTLINE)
    # bronze is too dark to carry dark lettering, so its plate is cut from the light bronze; silver and gold are bright enough as they are
    face = metal[0] if metal is BRZ else metal[1]
    cv.fill(px0, py0, px0 + pw, py0 + ph, face)
    cv.fill(px0, py0, px0 + pw, py0 + 1, mix(face, (255, 255, 255), 0.35))
    cv.fill(px0, py0 + ph - 1, px0 + pw, py0 + ph, metal[2] if metal is not BRZ else metal[1])
    for sx in (px0 + 2, px0 + pw - 4):
        cv.fill(sx, py0 + 2, sx + 2, py0 + 4, metal[2])
        cv.fill(sx, py0 + ph - 4, sx + 2, py0 + ph - 2, metal[2])
    star(cv, TEXT['icon_x'], TEXT['ability_y'] + 1, g.GOLD_D)
    orb(cv, TEXT['icon_x'], TEXT['item_y'] + 1, g.BURGUNDY)
    for x in range(14, CW - 14):
        if x % 3 != 2:
            cv.px(x, TEXT['rule_y'], g.PARCH_D)
    cv.fill(CW // 2 - 1, TEXT['rule_y'] - 1, CW // 2 + 2, TEXT['rule_y'] + 2, g.GOLD_D)
    for y in range(FIELD['y'], PLATE['y'] - 1):
        for x in range(FIELD['x'], FIELD['x'] + FIELD['w']):
            cv.clear(x, y)
    return cv


def field_layer(rarity):
    """The mosaic field with its nimbus (centred at 48,31) and a jewel for each step of rarity."""
    spec = RARITY[rarity]
    metal = spec['metal']
    cv = Sprite(FIELD['w'], FIELD['h'])
    tiles = [g.tile_field(spec['field'], spec['speck'], s) for s in (1, 2, 3, 4)]
    for y in range(FIELD['h']):
        for x in range(FIELD['w']):
            tt = tiles[((x // 16) + (y // 16) * 3) % 4]
            cv.px(x, y, tuple(int(v) for v in tt.img[y % 16, x % 16]), True)
    nimbus(cv, FIELD['w'] // 2, 31, 23, metal, spec['nimbus'])
    for i in range(spec['rank'] + 1):
        px, py = 6 + i * 7, 5
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
            cv.px(px + dx, py + dy, metal[0] if (dx, dy) == (0, 0) else metal[1], True)
    return cv


def sprite_origin(sp, nimbus_x, nimbus_y):
    """Where to draw a sprite so that what it actually draws, not its canvas, is centred on the nimbus."""
    ys, xs = np.where(sp[:, :, 3] > 127)
    return (int(round(nimbus_x - (xs.min() + xs.max() + 1) / 2.0)), int(round(nimbus_y - (ys.min() + ys.max() + 1) / 2.0)))


def make_card(card, chosen=False, hover=False, back=False):
    if back:
        return ring(back_layer(), chosen, hover)
    cv = Sprite(CW, CH)
    cv.paste(field_layer(card['rarity']), FIELD['x'], FIELD['y'])
    sp = load_sprite(card['species'])
    ox, oy = sprite_origin(sp, FIELD['x'] + FIELD['w'] // 2, FIELD['y'] + 31)
    cv.blit(sp, ox, oy)
    cv.paste(frame_layer(card['rarity']), 0, 0)
    metal = RARITY[card['rarity']]['metal']
    text_center(cv, PLATE['x'] + PLATE['w'] // 2, PLATE['y'] + 3, card['name'], g.INK)
    text_center(cv, PLATE['x'] + PLATE['w'] // 2, PLATE['y'] + 12, f"Lv {card['level']}  {card['nature']}", mix(g.INK, metal[1], 0.25))
    text(cv, TEXT['text_x'], TEXT['ability_y'], card['ability'], g.INK)
    text(cv, TEXT['text_x'], TEXT['item_y'], card['item'], g.INK)
    y = TEXT['move_y']
    for move in card['moves']:
        gem(cv, TEXT['icon_x'], y - 1, TYPE_COLORS[MOVE_TYPES[move]], category(move))
        text(cv, TEXT['gem_text_x'], y, MOVE_NAMES.get(move, move.title()), g.INK)
        y += TEXT['move_step']
    if chosen:
        seal(cv, CW - 20, 20)
    return ring(cv, chosen, hover)


def seal(cv, x, y):
    """A wax seal for a card that is being kept."""
    for yy in range(-7, 8):
        for xx in range(-7, 8):
            d = math.hypot(xx, yy)
            if d <= 7:
                cv.px(x + xx, y + yy, g.OUTLINE if d > 6 else g.BURGUNDY if d > 3 else g.BURGUNDY_L)
    for dx, dy in ((0, -3), (0, -2), (0, -1), (0, 0), (0, 1), (0, 2), (-2, -1), (-1, -1), (1, -1), (2, -1)):
        cv.px(x + dx, y + dy, g.GOLD_L)


def back_layer():
    """The face-down side: the same border (gold and lapis, so nothing about the rarity shows), a lapis lozenge field, and a mosaic
    pokeball inside a gold circle whose rim is its outline."""
    spec = RARITY['rare']
    metal = spec['metal']
    cv = Sprite(CW, CH)
    for y in range(CH):
        for x in range(CW):
            cv.px(x, y, g.PARCH, False)
    border_layer(cv, spec)
    ax0, ay0, aw = 8, 8, CW - 16
    inner_h = CH - 16
    for y in range(inner_h):
        for x in range(aw):
            d = abs((x % 16) - 7.5) + abs((y % 16) - 7.5)
            c = g.LAPIS[0] if d > 7 else g.GOLD_D if d > 6 else g.LAPIS[1] if d > 3 else g.RUBY[2] if (x + y) % 2 else g.RUBY[0]
            cv.px(ax0 + x, ay0 + y, c, True)
    cx, cy, big = ax0 + aw // 2, ay0 + inner_h // 2, 30
    r = random.Random(9)
    ball = big - 5
    pearl = [rgb('#F4EBD3'), rgb('#DCCFAE'), rgb('#B9AA86')]
    for y in range(cy - big, cy + big + 1):
        for x in range(cx - big, cx + big + 1):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > big:
                continue
            ang = math.degrees(math.atan2(y - cy, x - cx)) % 360
            if d > big - 1.2:
                c = metal[3]
            elif d > ball + 1.2:
                c = metal[0] if int(ang // 18) % 2 else metal[1]
            elif d > ball:
                c = g.OUTLINE
            else:
                top = (y + 0.5) < cy
                lit = (x + 0.5 - cx) + (y + 0.5 - cy) < -ball * 0.25
                shade = (x + 0.5 - cx) + (y + 0.5 - cy) > ball * 0.55
                if abs(y + 0.5 - cy) < 2.2:
                    c = g.OUTLINE if abs(y + 0.5 - cy) < 1.1 else metal[1]
                elif top:
                    c = g.RUBY[1] if lit else g.RUBY[2] if shade else g.RUBY[0]
                    if r.random() < 0.07:
                        c = metal[1]
                else:
                    c = pearl[0] if lit else pearl[2] if shade else pearl[1 if r.random() < 0.35 else 0]
                    if r.random() < 0.02:
                        c = pearl[2]
                if d < 6.5:
                    c = g.OUTLINE if d > 5.2 else metal[1] if d > 3.6 else pearl[0]
                    if d < 1.6:
                        c = g.RUBY[0]
            cv.px(x, y, c, True)
    return cv


def ring(cv, chosen, hover):
    n = 2 if chosen else 1 if hover else 0
    if not n:
        return cv
    out = Sprite(cv.w + n * 2, cv.h + n * 2)
    out.paste(cv, n, n)
    for y in range(out.h):
        for x in range(out.w):
            if out.alpha[y, x]:
                continue
            for k in range(1, n + 1):
                if any(0 <= x + dx < out.w and 0 <= y + dy < out.h and out.alpha[y + dy, x + dx]
                       for dx in range(-k, k + 1) for dy in range(-k, k + 1)):
                    out.px(x, y, g.GOLD_L if k == n else g.GOLD_D, True)
                    break
    return out


# ---- the scenes -----------------------------------------------------------------------------------------------------------------

PACK = [
    dict(species='arcanine', name='Arcanine', rarity='common', level=50, nature='Adamant', ability='Intimidate', item='Life Orb',
         moves=['flareblitz', 'extremespeed', 'closecombat', 'wildcharge']),
    dict(species='blastoise', name='Blastoise', rarity='uncommon', level=50, nature='Bold', ability='Torrent', item='Leftovers',
         moves=['scald', 'icebeam', 'rapidspin', 'darkpulse']),
    dict(species='dragonite', name='Dragonite', rarity='rare', level=50, nature='Adamant', ability='Multiscale', item='Lum Berry',
         moves=['extremespeed', 'dragonclaw', 'earthquake', 'firepunch']),
    dict(species='garchomp', name='Garchomp', rarity='epic', level=50, nature='Jolly', ability='Rough Skin', item='Life Orb',
         moves=['earthquake', 'outrage', 'stoneedge', 'firefang']),
    dict(species='kyogre', name='Kyogre', rarity='legendary', level=50, nature='Modest', ability='Drizzle', item='Choice Specs',
         moves=['originpulse', 'icebeam', 'thunder', 'scald']),
]


def oak_backdrop(cv):
    r = random.Random(4)
    for x in range(0, cv.w):
        plank = (x // 24) % 2
        for y in range(cv.h):
            n = 0.8 + 0.2 * r.random() if (x % 24) not in (0, 1) else 0.45
            base = (46, 33, 26) if plank else (38, 27, 21)
            cv.px(x, y, tuple(int(c * n) for c in base))
    cv.fill(0, 0, cv.w, 3, g.GOLD_S)
    cv.fill(0, cv.h - 3, cv.w, cv.h, g.GOLD_S)


def pack_mockup():
    W, H = 640, 360
    cv = Canvas(W, H)
    oak_backdrop(cv)
    text_center(cv, W // 2, 22, 'Pack 2 of 3', g.CREAM, g.OUTLINE)
    text_center(cv, W // 2, 34, 'Keep two cards', g.GOLD_L, g.OUTLINE)
    gap = 6
    x = (W - (5 * CW + 4 * gap)) // 2
    states = [dict(), dict(hover=True), dict(), dict(chosen=True), dict(chosen=True)]
    for i, card in enumerate(PACK):
        s = make_card(card, **states[i])
        pad = (s.w - CW) // 2
        lift = 8 if states[i].get('chosen') else 0
        cv.paste(s, x + i * (CW + gap) - pad, 68 - lift - pad)
    g.burgundy_button(cv, W // 2 - 60, 252, 120, 18, 'Keep these two')
    text_center(cv, W // 2, 282, 'Right-click a card to inspect it', rgb('#C6AC87'), g.OUTLINE)
    return cv


def ladder():
    pad, gap = 10, 10
    W = pad * 2 + 6 * CW + 5 * gap
    H = CH + 54
    cv = Canvas(W, H)
    oak_backdrop(cv)
    items = [('Face down', make_card(PACK[0], back=True))] + [(c['rarity'].title(), make_card(c)) for c in PACK]
    for i, (label, s) in enumerate(items):
        x = pad + i * (CW + gap)
        cv.paste(s, x, 12)
        text_center(cv, x + CW // 2, CH + 24, label, g.CREAM, g.OUTLINE)
    return cv


def gem_legend():
    names = list(TYPE_COLORS)
    cols = 6
    cell_w, cell_h = 58, 20
    W = cols * cell_w + 12
    H = (len(names) // cols) * cell_h + 54
    cv = Canvas(W, H, rgb('#E8D5A8'))
    text(cv, 8, 6, 'Shape: the kind of move', g.BROWN)
    for i, (shape, label) in enumerate((('physical', 'Physical'), ('special', 'Special'), ('status', 'Status'))):
        x = 8 + i * 96
        gem(cv, x, 18, TYPE_COLORS['fire'], shape)
        text(cv, x + 11, 18, label, g.INK)
    text(cv, 8, 34, 'Colour: the type', g.BROWN)
    for i, name in enumerate(names):
        x = 8 + (i % cols) * cell_w
        y = 46 + (i // cols) * cell_h
        gem(cv, x, y, TYPE_COLORS[name], 'special')
        text(cv, x + 10, y, name.title(), g.INK)
    return cv


def inspect_mockup():
    """The right-click inspect view: the specimen on its pedestal and a card that names every move's type and category."""
    W, H = 427, 240
    cv = Canvas(W, H)
    oak_backdrop(cv)
    card = PACK[4]
    spec = RARITY[card['rarity']]
    # the display case
    g.PixelUi = None
    x0, y0, w, h = 12, 28, 196, 180
    cv.fill(x0, y0, x0 + w, y0 + h, g.OUTLINE)
    cv.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + h - 1, g.GOLD_D)
    cv.fill(x0 + 3, y0 + 3, x0 + w - 3, y0 + h - 3, rgb('#1E1410'))
    for gy in range(y0 + 118, y0 + h - 3, 6):
        cv.fill(x0 + 3, gy, x0 + w - 3, gy + 1, rgb('#3A2A1C'))
    nimbus(cv, x0 + w // 2, y0 + 78, 40, spec['metal'], spec['nimbus'])
    sp = load_sprite(card['species'])
    ys, xs = np.where(sp[:, :, 3] > 127)
    big = np.kron(sp, np.ones((2, 2, 1), np.uint8))
    bys, bxs = np.where(big[:, :, 3] > 127)
    cv.blit(big, int(round(x0 + w // 2 - (bxs.min() + bxs.max() + 1) / 2)), int(round(y0 + 78 - (bys.min() + bys.max() + 1) / 2)))
    cv.fill(x0 + w // 2 - 40, y0 + 122, x0 + w // 2 + 40, y0 + 128, g.OUTLINE)
    cv.fill(x0 + w // 2 - 39, y0 + 123, x0 + w // 2 + 39, y0 + 127, spec['metal'][1])
    text(cv, x0 + 8, y0 + 8, card['name'].upper(), g.CREAM, g.OUTLINE)
    # the specimen card
    sx, sy, sw, sh = 218, 28, 197, 180
    cv.fill(sx, sy, sx + sw, sy + sh, g.OUTLINE)
    cv.fill(sx + 1, sy + 1, sx + sw - 1, sy + sh - 1, g.PARCH_D)
    cv.fill(sx + 2, sy + 2, sx + sw - 2, sy + sh - 2, g.PARCH)
    text(cv, sx + 10, sy + 10, card['name'], g.INK)
    text(cv, sx + 10, sy + 20, f"Level {card['level']}   {card['nature']} nature", g.BROWN)
    text(cv, sx + 10, sy + 30, 'Weather special attacker', g.BROWN)
    star(cv, sx + 10, sy + 45, g.GOLD_D)
    text(cv, sx + 19, sy + 44, 'Ability: ' + card['ability'], g.INK)
    orb(cv, sx + 10, sy + 56, g.BURGUNDY)
    text(cv, sx + 19, sy + 55, 'Item: ' + card['item'], g.INK)
    for x in range(sx + 10, sx + sw - 10):
        if x % 3 != 2:
            cv.px(x, sy + 70, g.PARCH_D)
    text(cv, sx + 10, sy + 76, 'Moves', g.BROWN)
    y = sy + 89
    for move in card['moves']:
        gem(cv, sx + 10, y, TYPE_COLORS[MOVE_TYPES[move]], category(move))
        text(cv, sx + 22, y, MOVE_NAMES.get(move, move.title()), g.INK)
        text(cv, sx + 22, y + 9, MOVE_TYPES[move].title() + ' - ' + category(move).title(), g.BROWN)
        y += 21
    g.burgundy_button(cv, 12, 214, 90, 18, 'Back')
    return cv


TYPE_ORDER = list(TYPE_COLORS)
SHAPE_ORDER = ['physical', 'special', 'status']


def save_rgba(sprite, path):
    arr = np.zeros((sprite.h, sprite.w, 4), np.uint8)
    arr[:, :, :3] = sprite.img
    arr[:, :, 3] = np.where(sprite.alpha, 255, 0)
    Image.fromarray(arr, 'RGBA').save(path)


def bake(out):
    """Writes the textures the mod draws a card from. The Java side (ByzantineCardFace) mirrors the layout constants above."""
    out.mkdir(parents=True, exist_ok=True)
    for rarity in RARITY:
        save_rgba(frame_layer(rarity), out / f'frame_{rarity}.png')
        save_rgba(field_layer(rarity), out / f'field_{rarity}.png')
    save_rgba(back_layer(), out / 'back.png')
    atlas = Sprite(7 * len(TYPE_ORDER), 7 * len(SHAPE_ORDER))
    for row, shape in enumerate(SHAPE_ORDER):
        for col, name in enumerate(TYPE_ORDER):
            gem(atlas, col * 7, row * 7, TYPE_COLORS[name], shape)
    save_rgba(atlas, out / 'gems.png')
    badge = Sprite(15, 15)
    seal(badge, 7, 7)
    save_rgba(badge, out / 'seal.png')
    (out / 'README.md').write_text(
        'Baked by docs/design/concepts/byzantine/rental_card.py (`python rental_card.py --bake`). Rental card layers: `field_<rarity>.png`\n'
        '(96x62, nimbus centred at 48,31), `frame_<rarity>.png` (112x160, window left clear), `back.png`, `gems.png` (7x7 gems: rows\n'
        f'{SHAPE_ORDER}, columns {TYPE_ORDER}), `seal.png`.\n', encoding='utf-8')


if __name__ == '__main__':
    if '--bake' in sys.argv:
        bake(g.ROOT / 'src/main/resources/assets/cobbletowers/textures/gui/byzantine')
        print('baked')
        sys.exit(0)
    render(pack_mockup(), 3).save(HERE / 'rental_pack_mockup.png')
    close = Canvas(CW + 24, CH + 24, g.rgb('#1B120D'))
    close.paste(make_card(PACK[4], chosen=True), 10, 10)
    render(close, 8).save(HERE / 'rental_card_closeup.png')
    render(ladder(), 3).save(HERE / 'rental_rarity_ladder.png')
    render(gem_legend(), 8).save(HERE / 'gem_legend.png')
    render(inspect_mockup(), 4).save(HERE / 'rental_inspect_mockup.png')
    print('wrote', sorted(p.name for p in HERE.glob('rental_*.png')) + ['gem_legend.png'])
