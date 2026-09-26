"""Procedural art for residual echoes: the residue entity texture (64x64, pale so the client tint gives the temper
color), the residual shard item (16x16) and the guide page (residues). Same style as graft_art.py: indigo striped
paper, bone + verdigris frame, flat shapes, navy outlines, 1:1 pixels.
Run: python tools/art/residue_art.py (needs Pillow). Rewrites textures/entity/residue.png,
textures/item/residual_shard.png and textures/gui/guide/residues.png."""
import random
from PIL import Image, ImageDraw

from graft_art import (ROOT, BONE, VERD, VERD_D, INK, DEEP, STRIPE, EMBER, RED, PINK, PINK_L, SHELL, INDIGO, SHADOW, GOLD,
                       TEMPERS, clean_page, rect, figure, ghost_figure, slip, arrow, dotted, ring, sparkle)

PALE = (236, 236, 244); PALE_M = (206, 208, 222); PALE_D = (168, 170, 190); HOLLOW = (70, 72, 100)

# ---------- entity texture ----------

def box_uv(u, v, w, h, d):
    """Minecraft box UV faces: returns dict face -> (x, y, w, h) on the sheet."""
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h),
    }

def fill_face(px, rng, face, base, mid, dark, fray=0.0, cracks=0):
    x0, y0, w, h = face
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            t = (y - y0) / max(1, h - 1)
            c = base if t < 0.35 else mid if t < 0.8 else dark
            if rng.random() < 0.12:
                c = mid if c == base else base
            a = 255
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            if fray and edge and rng.random() < fray:
                a = 0
            px[x, y] = c + (a,)
    for _ in range(cracks):
        cx, cy = x0 + rng.randrange(w), y0 + rng.randrange(h)
        for _ in range(rng.randrange(2, 4)):
            if x0 <= cx < x0 + w and y0 <= cy < y0 + h:
                px[cx, cy] = HOLLOW + (255,)
            cx += rng.choice((-1, 0, 1)); cy += 1

def residue_texture():
    rng = random.Random(1311)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    # head 6x6x6 at (0,0)
    head = box_uv(0, 0, 6, 6, 6)
    for name, face in head.items():
        fill_face(px, rng, face, PALE, PALE_M, PALE_D, fray=0.25 if name != 'front' else 0.0, cracks=1)
    fx, fy, _, _ = head['front']
    # hollow eyes and a missing corner: a face half remembered
    for x, y in ((1, 2), (4, 2)):
        px[fx + x, fy + y] = HOLLOW + (255,)
        px[fx + x, fy + y + 1] = HOLLOW + (255,)
    px[fx + 5, fy] = (0, 0, 0, 0); px[fx + 5, fy + 1] = (0, 0, 0, 0); px[fx + 4, fy] = (0, 0, 0, 0)
    # torso 6x8x3 at (0,14)
    for name, face in box_uv(0, 14, 6, 8, 3).items():
        fill_face(px, rng, face, PALE, PALE_M, PALE_D, fray=0.35, cracks=2 if name in ('front', 'back') else 0)
    tf = box_uv(0, 14, 6, 8, 3)['front']
    # a bright seam down the chest where the memory shows through
    for y in range(1, 7):
        px[tf[0] + 3, tf[1] + y] = (255, 255, 255, 255)
    px[tf[0] + 2, tf[1] + 3] = (255, 255, 255, 255)
    # arm 2x9x2 at (24,0)
    for face in box_uv(24, 0, 2, 9, 2).values():
        fill_face(px, rng, face, PALE, PALE_M, PALE_D, fray=0.3)
    # wisps: fade out towards the tip (alpha falls with each piece)
    for (u, v, w, h, d), alpha in (((0, 28, 4, 4, 2), 230), ((0, 36, 3, 4, 2), 170), ((0, 44, 2, 4, 1), 110)):
        for face in box_uv(u, v, w, h, d).values():
            fill_face(px, rng, face, PALE_M, PALE_D, PALE_D, fray=0.2)
            x0, y0, fw, fh = face
            for y in range(y0, y0 + fh):
                for x in range(x0, x0 + fw):
                    r, g, b, a = px[x, y]
                    if a:
                        px[x, y] = (r, g, b, alpha)
    # orbiting shard 2x3x1 at (40,0): near-white with a dark edge
    for face in box_uv(40, 0, 2, 3, 1).values():
        x0, y0, fw, fh = face
        for y in range(y0, y0 + fh):
            for x in range(x0, x0 + fw):
                px[x, y] = (255, 255, 255, 255) if (x + y) % 3 else PALE_D + (255,)
    return img

# ---------- item ----------

SHARD = [
    "................",
    "...........##...",
    "..........#ww#..",
    ".........#wwp#..",
    "........#wwpp#..",
    ".......#wwppl#..",
    "......#wwpplm#..",
    ".....#wwpplm#...",
    "....#wwpplm#....",
    "...#wwpplm#.....",
    "...#wpplm#......",
    "..#wpplm#.......",
    "..#pplm#........",
    "..#llm#.........",
    "...###..........",
    "................",
]

def shard_item():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    colors = {'#': INK, 'w': (246, 242, 250), 'p': PINK_L, 'l': (196, 170, 226), 'm': (122, 110, 178)}
    for y, row in enumerate(SHARD):
        for x, ch in enumerate(row):
            if ch in colors:
                px[x, y] = colors[ch] + (255,)
    # memory core and a verdigris glint, like the other memory items
    for x, y in ((7, 8), (8, 7), (6, 9)):
        px[x, y] = PINK + (255,)
    px[11, 3] = VERD + (255,)
    px[12, 1] = VERD + (255,); px[13, 2] = VERD_D + (255,)
    # two chips floating off the break
    px[1, 11] = PALE_M + (255,); px[0, 13] = PALE_D + (255,); px[14, 5] = PALE_M + (255,)
    return img

# ---------- guide page ----------

def residue_figure(img, x, y, body, alpha=150):
    """Fragment: head, torso, one arm, a tapering wisp; three shards around it."""
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    shade = tuple(max(0, v - 40) for v in body)
    rect(d, x + 2, y, 8, 8, body)
    rect(d, x + 3, y + 3, 2, 2, DEEP); rect(d, x + 7, y + 3, 2, 2, DEEP)
    rect(d, x + 9, y, 1, 2, (0, 0, 0, 0))
    rect(d, x + 1, y + 9, 10, 10, body)
    rect(d, x + 6, y + 10, 1, 7, (255, 255, 255))
    rect(d, x + 11, y + 9, 3, 9, shade)
    rect(d, x + 3, y + 19, 6, 4, body)
    rect(d, x + 4, y + 23, 4, 4, shade)
    rect(d, x + 5, y + 27, 2, 3, shade)
    for sx, sy in ((x - 6, y + 8), (x + 17, y + 4), (x + 15, y + 20)):
        rect(d, sx, sy, 2, 3, (255, 255, 255))
    r, g, b, a = layer.split()
    layer.putalpha(a.point(lambda v: alpha if v else 0))
    img.alpha_composite(layer)

def chunk_block(d, x, y, loud=True):
    rect(d, x + 1, y + 1, 26, 20, SHADOW)
    rect(d, x, y, 26, 20, (52, 48, 84))
    rect(d, x, y, 26, 3, (80, 74, 120))
    if loud:
        for i, (cx, cy) in enumerate([(6, 5), (9, 8), (8, 11), (12, 14), (15, 12), (18, 16)]):
            rect(d, x + cx, y + cy, 3, 2, RED)
        rect(d, x + 20, y + 5, 3, 3, EMBER)

def residues_page():
    img = clean_page().convert('RGBA')
    d = ImageDraw.Draw(img)
    fire = TEMPERS[2][2]; death = TEMPERS[1][2]; silence = TEMPERS[0][2]; fall = TEMPERS[3][2]
    # 1. an overloaded chunk condenses: cracked block -> residue rising out of it
    chunk_block(d, 14, 86)
    dotted(d, [(27, 84), (30, 70), (36, 58)], fire, 3)
    residue_figure(img, 30, 22, fire, 170)
    d = ImageDraw.Draw(img)
    # pressure falling: small bar with a down arrow
    rect(d, 14, 24, 6, 40, DEEP); rect(d, 15, 44, 4, 19, EMBER); rect(d, 15, 30, 4, 14, (60, 50, 70))
    rect(d, 22, 50, 2, 8, BONE); rect(d, 20, 56, 6, 2, BONE); rect(d, 21, 58, 4, 1, BONE)
    # 2. observe: the lens ring reads it; progress arc
    ring(d, 96, 40, 18, 5, VERD)
    d.ellipse([83, 27, 109, 53], fill=(40, 52, 96))
    residue_figure(img, 90, 26, silence, 235)
    d = ImageDraw.Draw(img)
    d.arc([74, 18, 118, 62], 200, 460, fill=PINK_L, width=2)
    # 3. capture: needle -> shard
    rect(d, 70, 86, 24, 2, BONE); rect(d, 66, 85, 4, 4, VERD); rect(d, 94, 86, 3, 1, BONE)
    arrow(d, 100, 87, 112)
    shard = shard_item().resize((32, 32), Image.NEAREST)
    img.alpha_composite(shard, (116, 72))
    d = ImageDraw.Draw(img)
    # 4. exploit: a kindled echo drinks a fire residue (dotted stream), and a slip comes out of the chunk
    residue_figure(img, 160, 18, fire, 150)
    d = ImageDraw.Draw(img)
    dotted(d, [(178, 38), (192, 44), (204, 42)], fire, 3)
    figure(d, 206, 22, fire)
    slip(d, 170, 60, fire, 12, 15)
    # 5. starve: mute stone under a fading, dotted residue
    rect(d, 177, 101, 18, 14, SHADOW); rect(d, 176, 100, 18, 14, (60, 64, 96)); rect(d, 179, 103, 12, 2, (100, 104, 140))
    residue_figure(img, 180, 70, death, 70)
    d = ImageDraw.Draw(img)
    # 6. old residue by the observatory reel (right bottom)
    rect(d, 214, 100, 20, 14, SHADOW); rect(d, 213, 99, 20, 14, (120, 96, 70)); ring(d, 223, 106, 5, 2, GOLD, hole=(120, 96, 70))
    residue_figure(img, 216, 64, fall, 190)
    d = ImageDraw.Draw(img)
    for sx, sy in ((212, 62), (238, 70), (230, 58)):
        sparkle(d, sx, sy, BONE)
    return img

if __name__ == '__main__':
    residue_texture().save(ROOT + 'entity/residue.png')
    shard_item().save(ROOT + 'item/residual_shard.png')
    residues_page().save(ROOT + 'gui/guide/residues.png')
    print('ok')
