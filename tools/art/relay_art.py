"""Procedural art for the echo relay and the archive vault: the relay thread item (a looped copper-and-bone thread with
two pink echo knots), the archive vault block (navy stratum-like stone, bone trim, a row of verdigris drawers; the lit
side glows pink while it draws; the top is an amethyst grille), and the guide pages (relay, vault). Same style as
graft_art.py, residue_art.py and storm_art.py: indigo striped paper, bone + verdigris frame, flat shapes, navy outlines,
1:1 pixels.
Run: python tools/art/relay_art.py (needs Pillow)."""
import random
from PIL import Image, ImageDraw

from graft_art import (ROOT, BONE, VERD, VERD_D, INK, DEEP, EMBER, RED, PINK, PINK_L, SHADOW, GOLD, TEMPERS, INDIGO, clean_page, rect,
                       figure, ghost_figure, slip, arrow, dotted, ring, keycap, V_LETTER, sparkle)
from residue_art import residue_figure, chunk_block

COPPER = (214, 126, 82); COPPER_D = (150, 80, 56); COPPER_L = (240, 170, 120)
AMETHYST = (170, 120, 230); AMETHYST_D = (104, 70, 168); AMETHYST_L = (214, 184, 250)
STONE = (40, 50, 96); STONE_M = (32, 40, 80); STONE_D = (22, 28, 60)

# ---------- item ----------

THREAD = [
    "................",
    "................",
    ".....cccccc.....",
    "...cc......cc...",
    "..c..bbbbbb..c..",
    "..c.b......b.c..",
    ".c.b..cccc..b.c.",
    ".c.b.c....c.b.c.",
    ".c.b.c....c.b.c.",
    ".c.b..cccc.#b.c.",
    "..c.b.....#pp#..",
    "..c..bbbbb#lp#..",
    "...cc......##...",
    ".##..cccccc.....",
    "#pp#............",
    "#lp#............",
]

def thread_item():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    colors = {'#': INK, 'p': PINK, 'l': PINK_L, 'c': COPPER, 'b': BONE}
    for y, row in enumerate(THREAD):
        for x, ch in enumerate(row):
            if ch in colors:
                px[x, y] = colors[ch] + (255,)
    return img

# ---------- blocks ----------

def stone_fill(px, rng, x0, y0, w, h):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            r = rng.random()
            px[x, y] = (STONE if r < 0.6 else STONE_M if r < 0.9 else STONE_D) + (255,)

def vault_side(lit=False):
    """Stone body, bone bands top and bottom, three drawers in the middle (verdigris idle, pink and glowing lit)."""
    rng = random.Random(4242)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
    px = img.load()
    stone_fill(px, rng, 0, 0, 16, 16)
    for x in range(16):
        px[x, 0] = DEEP + (255,); px[x, 15] = DEEP + (255,)
        px[x, 2] = BONE + (255,); px[x, 13] = BONE + (255,)
        px[x, 3] = (196, 186, 166, 255); px[x, 12] = (196, 186, 166, 255)
    for y in range(16):
        px[0, y] = DEEP + (255,); px[15, y] = DEEP + (255,)
    face, rim, spark = (PINK, PINK_L, (255, 250, 252)) if lit else (VERD_D, VERD, BONE)
    for i, dx in enumerate((2, 6, 10)):
        for y in range(6, 10):
            for x in range(dx, dx + 4):
                px[x, y] = face + (255,)
        for x in range(dx, dx + 4):
            px[x, 5] = INK + (255,)
            px[x, 10] = SHADOW + (255,)
        px[dx, 6] = rim + (255,)
        px[dx + 1, 8] = INK + (255,); px[dx + 2, 8] = INK + (255,)  # the handle
        if lit and i == 1:
            px[dx + 3, 6] = spark + (255,)
    if lit:
        for x, y in ((4, 4), (11, 4), (7, 11)):
            px[x, y] = PINK_L + (255,)
    return img

def vault_top():
    """Bone rim, amethyst grille over a dark well."""
    rng = random.Random(99)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
    px = img.load()
    stone_fill(px, rng, 0, 0, 16, 16)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = DEEP + (255,)
    for i in range(2, 14):
        for (x, y) in ((i, 2), (i, 13), (2, i), (13, i)):
            px[x, y] = BONE + (255,)
    for y in range(3, 13):
        for x in range(3, 13):
            px[x, y] = SHADOW + (255,)
    for k in (5, 8, 11):
        for t in range(3, 13):
            px[k, t] = AMETHYST_D + (255,)
            px[t, k] = AMETHYST_D + (255,)
    for k in (5, 8, 11):
        for j in (5, 8, 11):
            px[k, j] = AMETHYST_L + (255,)
    px[6, 6] = AMETHYST + (255,); px[9, 10] = AMETHYST + (255,)
    return img

# ---------- guide pages ----------

def thread_line(d, pts, noisy=False):
    c = RED if noisy else PINK_L
    for i in range(len(pts) - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        steps = max(abs(x1 - x0), abs(y1 - y0)) // 3
        for s in range(steps + 1):
            x = x0 + (x1 - x0) * s // max(1, steps)
            y = y0 + (y1 - y0) * s // max(1, steps)
            if noisy and s % 3 == 1:
                y += 2
            rect(d, x, y, 2, 2, c)

def relay_page():
    img = clean_page().convert('RGBA')
    d = ImageDraw.Draw(img)
    hushed = TEMPERS[0][2]
    # left: a hushed echo at home, a miner far away: one thread; the work imprint is swallowed
    ring(d, 22, 40, 20, 2, hushed)
    figure(d, 14, 26, hushed)
    figure(d, 72, 26, (206, 196, 214))
    rect(d, 88, 56, 12, 10, (90, 92, 110)); rect(d, 89, 57, 10, 2, (140, 140, 160))
    thread_line(d, [(28, 30), (50, 20), (78, 28)])
    slip(d, 92, 34, TEMPERS[0][1], 10, 12)
    rect(d, 90, 38, 14, 2, RED)  # crossed out: no work imprint
    thread = thread_item().resize((32, 32), Image.NEAREST)
    img.alpha_composite(thread, (36, 70))
    d = ImageDraw.Draw(img)
    # middle: possessing one end, sneak + V hops into the other; the left body stands back up
    ghost_figure(img, 118, 20, (206, 196, 214), 120)
    d = ImageDraw.Draw(img)
    figure(d, 168, 20, PINK)
    thread_line(d, [(132, 28), (170, 28)])
    arrow(d, 136, 58, 166)
    keycap(d, 140, 66, V_LETTER)
    rect(d, 124, 70, 12, 4, BONE)  # sneak bar
    # mirror: a block broken here is broken there
    for bx, by in ((118, 92), (168, 92)):
        rect(d, bx, by, 12, 12, (110, 112, 130)); rect(d, bx + 1, by + 1, 10, 3, (150, 150, 170))
    rect(d, 132, 94, 12, 12, INK); rect(d, 182, 94, 12, 12, INK)
    dotted(d, [(146, 100), (176, 100)], PINK_L, 4)
    # right: a fracture under one end = noise; a death snaps the thread
    chunk_block(d, 208, 86, loud=True)
    figure(d, 212, 20, (206, 196, 214))
    thread_line(d, [(206, 30), (236, 60)], noisy=True)
    figure(d, 226, 52, (206, 196, 214))
    for sx, sy in ((238, 50), (232, 44)):
        sparkle(d, sx, sy, RED)
    return img

def vault_page():
    img = clean_page().convert('RGBA')
    d = ImageDraw.Draw(img)
    # left: the vault draws the loudest imprint out of the 3x3 chunks
    for gx in range(3):
        for gy in range(3):
            chunk_block(d, 10 + gx * 28, 30 + gy * 22, loud=(gx, gy) in ((0, 0), (2, 1), (1, 2)))
    lit = vault_side(True).resize((24, 24), Image.NEAREST)
    img.alpha_composite(lit, (37, 50))
    d = ImageDraw.Draw(img)
    for sx, sy in ((20, 38), (72, 60), (46, 84)):
        dotted(d, [(sx, sy), (48, 62)], PINK_L, 4)
    # its chunk weighs a little: a bar of bleed under it
    rect(d, 14, 106, 72, 5, DEEP); rect(d, 15, 107, 18, 3, GOLD)
    # middle: move (carried, it leaks) and spend (needle -> slip, echo fed)
    idle = vault_side(False).resize((20, 20), Image.NEAREST)
    img.alpha_composite(idle, (112, 20))
    d = ImageDraw.Draw(img)
    arrow(d, 136, 30, 150)
    slip(d, 154, 20, TEMPERS[1][1], 12, 16)
    figure(d, 112, 62, (206, 196, 214))
    img.alpha_composite(vault_side(False).resize((10, 10), Image.NEAREST), (124, 78))
    d = ImageDraw.Draw(img)
    dotted(d, [(128, 92), (128, 104)], TEMPERS[1][2], 3)
    figure(d, 158, 62, TEMPERS[2][2])
    dotted(d, [(150, 76), (160, 76)], TEMPERS[2][2], 3)
    # right: fracture ruptures it (half spills), an archivist raids it
    chunk_block(d, 196, 20, loud=True)
    img.alpha_composite(vault_side(True).resize((20, 20), Image.NEAREST), (222, 22))
    d = ImageDraw.Draw(img)
    for i, (x, y) in enumerate(((220, 48), (232, 52), (244, 46), (226, 58))):
        rect(d, x, y, 3, 3, RED if i % 2 else EMBER)
    rect(d, 208, 78, 10, 22, (60, 64, 92)); rect(d, 206, 72, 14, 8, (80, 84, 120)); rect(d, 210, 75, 2, 2, GOLD); rect(d, 214, 75, 2, 2, GOLD)
    img.alpha_composite(vault_side(False).resize((16, 16), Image.NEAREST), (228, 86))
    d = ImageDraw.Draw(img)
    arrow(d, 226, 94, 220)
    return img

if __name__ == '__main__':
    thread_item().save(ROOT + 'item/relay_thread.png')
    vault_side(False).save(ROOT + 'block/archive_vault_side.png')
    vault_side(True).save(ROOT + 'block/archive_vault_side_on.png')
    vault_top().save(ROOT + 'block/archive_vault_top.png')
    relay_page().save(ROOT + 'gui/guide/relay.png')
    vault_page().save(ROOT + 'gui/guide/vault.png')
    print('ok')
