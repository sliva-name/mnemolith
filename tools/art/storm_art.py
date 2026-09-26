"""Procedural art for recollection storms and the Scar: the Scar entity texture (64x64, the residue model's UV layout,
darker and cracked; the client tint drifts through the merged tempers), the scar fragment item, scar glass
(semi-transparent, so it renders in the translucent layer), the scar heart and its base (block), and the guide pages
(storms, scar). Same style as graft_art.py and residue_art.py: indigo striped paper, bone + verdigris frame, flat
shapes, navy outlines, 1:1 pixels, violet/magenta for the Scar.
Run: python tools/art/storm_art.py (needs Pillow)."""
import random
from PIL import Image, ImageDraw

from graft_art import (ROOT, BONE, VERD, VERD_D, INK, DEEP, EMBER, RED, PINK, PINK_L, SHADOW, GOLD, TEMPERS, clean_page, rect,
                       figure, slip, arrow, dotted, ring, sparkle)
from residue_art import box_uv, fill_face, residue_figure, chunk_block, shard_item, PALE, PALE_M, PALE_D, HOLLOW

VIOLET = (150, 96, 214); VIOLET_D = (92, 54, 150); VIOLET_L = (206, 170, 246); MAGENTA = (232, 104, 214)
CRACK = (34, 20, 60); GLOW = (255, 236, 252)

# ---------- entity ----------

def scar_texture():
    """Residue layout, but greyer and crossed with glowing cracks: tinted, it reads as a dark fragment of many colors."""
    rng = random.Random(2609)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    base, mid, dark = (196, 192, 210), (160, 154, 180), (118, 112, 140)
    head = box_uv(0, 0, 6, 6, 6)
    for name, face in head.items():
        fill_face(px, rng, face, base, mid, dark, fray=0.15 if name != 'front' else 0.0, cracks=2)
    fx, fy, _, _ = head['front']
    for x, y in ((1, 2), (4, 2)):
        px[fx + x, fy + y] = GLOW + (255,)
        px[fx + x, fy + y + 1] = MAGENTA + (255,)
    for x in range(1, 5):
        px[fx + x, fy + 4] = CRACK + (255,)
    for name, face in box_uv(0, 14, 6, 8, 3).items():
        fill_face(px, rng, face, base, mid, dark, fray=0.2, cracks=3)
    tf = box_uv(0, 14, 6, 8, 3)['front']
    # the seam is a jagged crack of light across the chest
    for i, (x, y) in enumerate(((1, 1), (2, 2), (3, 2), (3, 3), (4, 4), (3, 5), (2, 6), (3, 7))):
        px[tf[0] + x, tf[1] + y] = (GLOW if i % 2 else MAGENTA) + (255,)
    for face in box_uv(24, 0, 2, 9, 2).values():
        fill_face(px, rng, face, base, mid, dark, fray=0.2, cracks=1)
    for (u, v, w, h, d), alpha in (((0, 28, 4, 4, 2), 240), ((0, 36, 3, 4, 2), 200), ((0, 44, 2, 4, 1), 150)):
        for face in box_uv(u, v, w, h, d).values():
            fill_face(px, rng, face, mid, dark, dark, fray=0.15, cracks=1)
            x0, y0, fw, fh = face
            for y in range(y0, y0 + fh):
                for x in range(x0, x0 + fw):
                    r, g, b, a = px[x, y]
                    if a:
                        px[x, y] = (r, g, b, alpha)
    for face in box_uv(40, 0, 2, 3, 1).values():
        x0, y0, fw, fh = face
        for y in range(y0, y0 + fh):
            for x in range(x0, x0 + fw):
                px[x, y] = (GLOW if (x + y) % 2 else VIOLET_L) + (255,)
    return img

# ---------- items and blocks ----------

FRAGMENT = [
    "................",
    "......##........",
    ".....#vv#.......",
    "....#vlgv#......",
    "....#vlgmv##....",
    "...#vlggmvvv#...",
    "...#vlgmmkvdd#..",
    "..#vlgmkkvddd#..",
    "..#vlmk#vddd#...",
    "..#vmk#.#dd#....",
    "...#k#...##.....",
    "...#v#..........",
    "....#...........",
    "................",
    "................",
    "................",
]

def fragment_item():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    colors = {'#': INK, 'v': VIOLET, 'l': VIOLET_L, 'g': GLOW, 'm': MAGENTA, 'k': CRACK, 'd': VIOLET_D}
    for y, row in enumerate(FRAGMENT):
        for x, ch in enumerate(row):
            if ch in colors:
                px[x, y] = colors[ch] + (255,)
    # temper motes: one of each color drifting off the break
    for (x, y), (_, _, c) in zip(((13, 9), (12, 12), (8, 12), (14, 4), (10, 14)), TEMPERS):
        px[x, y] = c + (255,)
    return img

def scar_glass():
    """Violet glass with a bright rim and a few dark cracks; the pane itself is see-through (alpha 90-130)."""
    rng = random.Random(77)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                px[x, y] = VIOLET_D + (230,)
            else:
                c = VIOLET if rng.random() < 0.8 else VIOLET_L
                px[x, y] = c + (100 + rng.randrange(30),)
    for x, y in ((1, 1), (2, 1), (1, 2), (14, 14), (13, 14), (14, 13)):
        px[x, y] = VIOLET_L + (220,)
    x, y = 4, 2
    for _ in range(11):
        px[x, y] = CRACK + (200,)
        x += rng.choice((0, 1)); y += 1
    x, y = 12, 5
    for _ in range(6):
        px[x, y] = CRACK + (200,)
        x -= rng.choice((0, 1)); y += 1
    px[9, 7] = GLOW + (230,); px[6, 11] = MAGENTA + (220,)
    return img

def scar_heart():
    """Crystal: violet facets with a glowing core line and cracks; the model samples several strips of it."""
    rng = random.Random(5)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            t = (x + y) % 6
            c = VIOLET_L if t == 0 else VIOLET if t < 4 else VIOLET_D
            if rng.random() < 0.08:
                c = MAGENTA
            px[x, y] = c + (255,)
    for y in range(16):
        px[5, y] = GLOW + (255,) if y % 3 else MAGENTA + (255,)
        px[13, y] = GLOW + (255,) if y % 4 else PINK + (255,)
    for x, y in ((1, 3), (2, 4), (2, 5), (3, 6), (9, 9), (10, 10), (10, 11), (11, 12)):
        px[x, y] = CRACK + (255,)
    return img

def scar_heart_base():
    """The ground the heart breaks out of: dark stone veined with violet light."""
    rng = random.Random(9)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 44 + rng.randrange(18)
            px[x, y] = (v, v - 6, v + 16, 255)
    for (x, y) in ((2, 13), (3, 12), (4, 12), (5, 13), (8, 14), (9, 13), (10, 13), (12, 12), (13, 13), (7, 3), (8, 4), (8, 5), (9, 6)):
        px[x, y] = VIOLET + (255,)
    for x in range(16):
        px[x, 12] = (px[x, 12][0] + 20, px[x, 12][1] + 10, px[x, 12][2] + 30, 255)
    return img

# ---------- guide pages ----------

def storm_cloud(d, x, y, w, c=(70, 56, 112)):
    for i in range(0, w, 10):
        d.ellipse([x + i, y + (i * 7) % 5, x + i + 18, y + 12 + (i * 7) % 5], fill=c)
    rect(d, x + 4, y + 8, w + 6, 6, c)

def storms_page():
    img = clean_page().convert('RGBA')
    d = ImageDraw.Draw(img)
    # left: the area (3x3 chunks) with its flickering edge; a shard freed at the centre calls the storm
    for gx in range(3):
        for gy in range(3):
            chunk_block(d, 10 + gx * 28, 34 + gy * 22, loud=(gx, gy) in ((1, 1), (0, 2), (2, 0)))
    for i in range(0, 84, 6):
        rect(d, 8 + i, 30, 3, 2, PINK_L); rect(d, 8 + i, 102, 3, 2, PINK_L)
    for i in range(0, 72, 6):
        rect(d, 6, 32 + i, 2, 3, PINK_L); rect(d, 96, 32 + i, 2, 3, PINK_L)
    storm_cloud(d, 14, 10, 74)
    for bx in (30, 52, 72):
        dotted(d, [(bx, 24), (bx - 3, 32)], VIOLET_L, 2)
    shard = shard_item()
    img.alpha_composite(shard, (44, 58))
    d = ImageDraw.Draw(img)
    # middle: waves condense residues; one acts out (a fire), one is read (lens ring), one starves (mute stone)
    arrow(d, 104, 66, 118)
    fire, death, silence, fall = TEMPERS[2][2], TEMPERS[1][2], TEMPERS[0][2], TEMPERS[3][2]
    residue_figure(img, 126, 16, fire, 190)
    d = ImageDraw.Draw(img)
    rect(d, 124, 50, 4, 5, EMBER); rect(d, 129, 48, 3, 7, RED); rect(d, 133, 51, 3, 4, EMBER)
    ring(d, 170, 30, 15, 4, VERD)
    d.ellipse([159, 19, 181, 41], fill=(40, 52, 96))
    residue_figure(img, 164, 16, silence, 235)
    d = ImageDraw.Draw(img)
    rect(d, 127, 101, 18, 14, SHADOW); rect(d, 126, 100, 18, 14, (60, 64, 96)); rect(d, 129, 103, 12, 2, (100, 104, 140))
    residue_figure(img, 130, 66, death, 70)
    d = ImageDraw.Draw(img)
    # a hushed echo swallowing an act-out
    figure(d, 166, 70, silence)
    dotted(d, [(186, 76), (196, 72)], silence, 3)
    # right: the bar and six notches, then three standing residues converge (the Scar)
    rect(d, 204, 14, 44, 6, DEEP); rect(d, 205, 15, 28, 4, MAGENTA)
    for i in range(6):
        rect(d, 205 + i * 7, 21, 1, 3, BONE)
    for (x, y), (_, _, c) in zip(((204, 40), (228, 40), (216, 72)), (TEMPERS[0], TEMPERS[3], TEMPERS[1])):
        residue_figure(img, x, y, c, 150)
    d = ImageDraw.Draw(img)
    dotted(d, [(212, 68), (222, 96)], VIOLET_L, 3); dotted(d, [(236, 68), (226, 96)], VIOLET_L, 3)
    rect(d, 218, 100, 10, 14, VIOLET); rect(d, 220, 96, 6, 4, VIOLET_L); rect(d, 222, 102, 2, 10, GLOW)
    return img

def scar_page():
    img = clean_page().convert('RGBA')
    d = ImageDraw.Draw(img)
    # the site: heart in a ring of scar glass
    rect(d, 12, 104, 100, 8, (48, 42, 70))
    glass = scar_glass().resize((12, 12), Image.NEAREST)
    for gx in (14, 30, 82, 98):
        img.alpha_composite(glass, (gx, 92))
    img.alpha_composite(glass, (30, 80))
    heart = scar_heart().resize((14, 22), Image.NEAREST)
    img.alpha_composite(heart, (55, 82))
    d = ImageDraw.Draw(img)
    for sx, sy in ((50, 76), (74, 80), (62, 70)):
        sparkle(d, sx, sy, VIOLET_L)
    # the Scar over it: a big cracked fragment, tinted in bands of the merged tempers
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    cols = [TEMPERS[0][2], TEMPERS[1][2], TEMPERS[3][2]]
    rect(ld, 46, 10, 30, 22, cols[0]); rect(ld, 50, 17, 6, 5, GLOW); rect(ld, 66, 17, 6, 5, GLOW)
    rect(ld, 42, 34, 38, 26, cols[1]); rect(ld, 80, 34, 10, 24, cols[2])
    for i, (x, y) in enumerate(((58, 36), (60, 40), (62, 42), (60, 46), (63, 50), (61, 54))):
        rect(ld, x, y, 3, 3, GLOW if i % 2 else MAGENTA)
    rect(ld, 52, 60, 18, 8, cols[2]); rect(ld, 56, 68, 10, 6, cols[1])
    r, g, b, a = layer.split()
    layer.putalpha(a.point(lambda v: 210 if v else 0))
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    # read it first: lens ring with a progress arc, then the blow
    ring(d, 138, 38, 16, 4, VERD)
    d.arc([118, 18, 158, 58], 200, 460, fill=PINK_L, width=2)
    rect(d, 132, 34, 12, 8, VIOLET)
    arrow(d, 160, 38, 174)
    rect(d, 178, 30, 3, 16, BONE); rect(d, 175, 44, 9, 2, GOLD); rect(d, 178, 46, 3, 4, (120, 90, 60))
    # drops: fragment -> your echo becomes scar-set (three slips)
    frag = fragment_item().resize((32, 32), Image.NEAREST)
    img.alpha_composite(frag, (124, 74))
    d = ImageDraw.Draw(img)
    arrow(d, 158, 90, 172)
    figure(d, 180, 74, (206, 170, 246))
    rect(d, 186, 84, 1, 8, GLOW)
    for i in range(3):
        slip(d, 204 + i * 14, 80, VIOLET, 12, 15)
    # a grave echo as decoy (top right)
    figure(d, 216, 22, TEMPERS[1][2])
    dotted(d, [(196, 28), (212, 34)], TEMPERS[1][2], 3)
    return img

if __name__ == '__main__':
    scar_texture().save(ROOT + 'entity/scar.png')
    fragment_item().save(ROOT + 'item/scar_fragment.png')
    scar_glass().save(ROOT + 'block/scar_glass.png')
    scar_heart().save(ROOT + 'block/scar_heart.png')
    scar_heart_base().save(ROOT + 'block/scar_heart_base.png')
    storms_page().save(ROOT + 'gui/guide/storms.png')
    scar_page().save(ROOT + 'gui/guide/scar.png')
    print('ok')
