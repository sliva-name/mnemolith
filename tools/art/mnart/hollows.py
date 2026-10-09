"""Memory Hollows art: hollow turf, hollowstone and its bricks, recollite ore and block (32x32 cube faces), the
forget-me-not (a 32x32 cross sprite) and the recollite shard item. Same light, outline and material library as the
rest of the mod: pale stone with violet-leaning shadows, faded lilac turf, icy recollite crystal."""
import math

import numpy as np

from . import core, shapes
from .core import Canvas, m_rect, m_poly, m_line, m_ellipse, erode
from .materials import surface
from .palette import RAMPS, mix, SHADOW

S = 32


def _vein(cv, rng, ramp_i, alpha, n=3):
    """Faint wandering memory veins, wrapped so the face still tiles."""
    for _ in range(n):
        x, y = rng.randrange(S), rng.randrange(S)
        dx = rng.choice((-1, 1))
        for _ in range(rng.randint(4, 8)):
            col = tuple(int(v) for v in cv.rgb[y % S, x % S])
            cv.px(x % S, y % S, mix(col, RAMPS['recollite'][ramp_i], alpha))
            x += dx if rng.random() < 0.7 else 0
            y += 1 if rng.random() < 0.6 else 0


def hollowstone_tex():
    rng = core.rng_for('hollowstone')
    cv = surface('hollowstone', S, S, rng, face='side', bevel=0, wear=0.45, tile=True)
    _vein(cv, rng, 5, 0.35)
    return cv


def hollowstone():
    return hollowstone_tex().image()


def hollowstone_bricks():
    rng = core.rng_for('hollowstone_bricks')
    cv = surface('hollowstone', S, S, rng, face='side', bevel=0, wear=0.35, tile=True, base_shift=0.3)
    for y in range(0, S, 8):
        cv.shade_px(m_rect(S, S, 0, y + 7, S - 1, y + 7), 0.42)
        cv.shade_px(m_rect(S, S, 0, y, S - 1, y), 0.18, toward=(255, 250, 240))
        off = 4 if (y // 8) % 2 else 0
        for x in range(off, S, 8):
            cv.shade_px(m_rect(S, S, x, y, x, min(S - 1, y + 6)), 0.38)
    for _ in range(6):
        cv.px(rng.randrange(S), rng.randrange(S), RAMPS['recollite'][4])
    return cv.image()


def _crystal(cv, cx, cy, size, rng):
    a = rng.random() * math.pi
    pts = []
    for k in range(5):
        ang = a + k * 2 * math.pi / 5 + rng.uniform(-0.3, 0.3)
        r = size * (1.0 if k % 2 == 0 else 0.6)
        pts.append((cx + math.cos(ang) * r, cy + math.sin(ang) * r))
    m = shapes.gem(cv, pts, 'recollite', 4)
    # dark seat so the crystal reads as set into the stone
    ring = core.dilate(m) & ~m
    cv.shade_px(ring, 0.35)
    cv.px(int(cx - size * 0.3), int(cy - size * 0.3), RAMPS['recollite'][6])
    return m


def recollite_ore():
    rng = core.rng_for('recollite_ore')
    cv = hollowstone_tex()
    for cx, cy, s in ((8, 9, 3.6), (22, 6, 2.8), (24, 21, 3.8), (10, 24, 2.6), (16, 15, 2.2)):
        _crystal(cv, cx + rng.uniform(-1, 1), cy + rng.uniform(-1, 1), s, rng)
    shapes.glow(cv, 16, 16, 22, RAMPS['recollite'][5], 0.12)
    return cv.image()


def recollite_block():
    rng = core.rng_for('recollite_block')
    cv = surface('recollite', S, S, rng, face='side', bevel=0, wear=0, tile=True, base_shift=-0.4)
    # cut panels: four facetted quarters with a lit top-left and a dark bottom-right seam
    for k in (0, 16):
        cv.shade_px(m_rect(S, S, 0, k, S - 1, k), 0.25, toward=(255, 255, 255))
        cv.shade_px(m_rect(S, S, k, 0, k, S - 1), 0.2, toward=(255, 255, 255))
        cv.shade_px(m_rect(S, S, 0, k + 15, S - 1, k + 15), 0.35)
        cv.shade_px(m_rect(S, S, k + 15, 0, k + 15, S - 1), 0.3)
    for gx, gy in ((5, 4), (21, 4), (5, 20), (21, 20)):
        core.glint(cv, gx, gy, arm=1)
    return cv.image()


def turf_top():
    rng = core.rng_for('hollow_turf_top')
    cv = surface('lilac', S, S, rng, face='top', bevel=0, wear=0, tile=True)
    # blades: short paired strokes, a lit tip over a dark root
    for _ in range(70):
        x, y = rng.randrange(S), rng.randrange(S)
        cv.px(x, y, RAMPS['lilac'][5 if rng.random() < 0.5 else 4])
        cv.px(x, (y + 1) % S, RAMPS['lilac'][2])
    for _ in range(5):
        cv.px(rng.randrange(S), rng.randrange(S), RAMPS['pale'][6])
    return cv.image()


def turf_side():
    rng = core.rng_for('hollow_turf_side')
    cv = surface('leather', S, S, rng, face='side', bevel=0, wear=0.3, tile=True)
    # the dirt under faded turf is a little greyer than a vanilla grass block's
    cv.shade_px(m_rect(S, S, 0, 0, S - 1, S - 1), 0.12, toward=tuple(RAMPS['pale'][2]))
    for x in range(S):
        d = 4 + int(2 * math.sin(x * 0.7 + 1.3)) + rng.randrange(0, 2)
        for y in range(d):
            i = 2 if y == d - 1 else (4 if y == 0 else 3)
            cv.px(x, y, RAMPS['lilac'][i])
        if rng.random() < 0.3:
            cv.px(x, d, RAMPS['lilac'][2])
    return cv.image()


def forget_me_not():
    rng = core.rng_for('forget_me_not')
    cv = Canvas(S, S)
    stems = [((15, 31), (14, 18)), ((15, 31), (21, 13)), ((16, 31), (9, 12)), ((16, 31), (18, 22))]
    for a, b in stems:
        cv.fill(core.m_line(S, S, a, b, 1), RAMPS['verdigris'][2])
    for a, b in ((15, 26), (17, 24)), ((15, 27), (12, 24)):
        cv.fill(core.m_line(S, S, a, b, 1), RAMPS['verdigris'][3])
    cv.fill(m_ellipse(S, S, 12.5, 24.5, 2.2, 1.2), RAMPS['verdigris'][3])
    cv.fill(m_ellipse(S, S, 18.5, 25.5, 2.2, 1.2), RAMPS['verdigris'][4])
    for cx, cy in ((14, 17), (21, 12), (9, 11), (18, 21), (24, 17)):
        for k in range(5):
            ang = k * 2 * math.pi / 5 - math.pi / 2
            px, py = cx + math.cos(ang) * 1.8, cy + math.sin(ang) * 1.8
            petal = m_ellipse(S, S, px + 0.5, py + 0.5, 1.4, 1.4)
            cv.fill(petal, mix(RAMPS['indigo'][6], RAMPS['glass'][5], 0.35) if k in (0, 4) else mix(RAMPS['indigo'][5], RAMPS['glass'][4], 0.3))
        cv.px(cx, cy, RAMPS['brass'][5])
        cv.px(cx - 1, cy - 2, RAMPS['indigo'][6])
    cv.outline(selout=0.5)
    return cv.image()


def recollite_shard():
    rng = core.rng_for('recollite_shard')
    cv = Canvas(S, S)
    side = shapes.gem(cv, [(5, 26), (8, 18), (14, 16), (13, 24), (8, 29)], 'recollite', 3)
    main = shapes.gem(cv, [(10, 27), (11, 16), (19, 6), (26, 3), (25, 12), (18, 23)], 'recollite', 4)
    seam = core.m_lines(S, S, [(12, 25), (16, 16), (22, 8)], 1) & main
    cv.fill(seam, RAMPS['recollite'][6])
    shapes.glow(cv, 18, 14, 11, RAMPS['pale'][6], 0.25)
    core.glint(cv, 23, 6, arm=1)
    for _ in range(3):
        x, y = rng.randrange(3, 29), rng.randrange(3, 29)
        if not (main | side)[y, x]:
            cv.px(x, y, RAMPS['recollite'][5], 190)
    cv.outline(selout=0.55)
    return cv.image()


BLOCK_TEXTURES = {
    'hollow_turf_top': turf_top,
    'hollow_turf_side': turf_side,
    'hollowstone': hollowstone,
    'hollowstone_bricks': hollowstone_bricks,
    'recollite_ore': recollite_ore,
    'recollite_block': recollite_block,
    'forget_me_not': forget_me_not,
}
ITEM_TEXTURES = {
    'recollite_shard': recollite_shard,
}
