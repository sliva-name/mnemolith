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


def _lantern_frame(seed):
    rng = core.rng_for(seed)
    cv = surface('hollowstone', S, S, rng, face='side', bevel=0, wear=0.3, tile=False, base_shift=0.3)
    rim = m_rect(S, S, 0, 0, S - 1, S - 1) & ~m_rect(S, S, 3, 3, S - 4, S - 4)
    cv.shade_px(m_rect(S, S, 0, 0, S - 1, 0) | m_rect(S, S, 0, 0, 0, S - 1), 0.25, toward=(255, 250, 240))
    cv.shade_px(m_rect(S, S, 0, S - 1, S - 1, S - 1) | m_rect(S, S, S - 1, 0, S - 1, S - 1), 0.4)
    return cv, rng, rim


def lantern_side():
    """A hollowstone frame around a cold recollite pane, a forget-me-not glowing inside."""
    cv, rng, rim = _lantern_frame('recollite_lantern_side')
    pane = m_rect(S, S, 5, 5, S - 6, S - 6)
    cv.part(pane, 'recollite', 'flat', 5, rng=rng, noise=0.25)
    cv.shade_px(m_rect(S, S, 5, 5, S - 6, 5) | m_rect(S, S, 5, 5, 5, S - 6), 0.4)
    # mullions: a cross of hollowstone
    cv.part(m_rect(S, S, 15, 5, 16, S - 6) | m_rect(S, S, 5, 15, S - 6, 16), 'pale', 'flat', 3)
    shapes.glow(cv, 15.5, 15.5, 10, RAMPS['pale'][6], 0.45)
    for (x, y) in ((10, 10), (21, 10), (10, 21), (21, 21)):
        cv.fill(m_ellipse(S, S, x + 0.5, y + 0.5, 1.6, 1.6), RAMPS['recollite'][6])
        cv.px(x, y, RAMPS['pale'][6])
    return cv.image()


def lantern_top():
    """The lantern's cap: hollowstone with a round recollite vent in the middle."""
    cv, rng, rim = _lantern_frame('recollite_lantern_top')
    vent = m_ellipse(S, S, 15.5, 15.5, 7, 7)
    cv.part(vent, 'recollite', 'flat', 5, rng=rng, noise=0.2)
    cv.shade_px(m_ellipse(S, S, 15.5, 15.5, 7, 7) & ~m_ellipse(S, S, 15.5, 15.5, 6, 6), 0.45)
    shapes.glow(cv, 15.5, 15.5, 6, RAMPS['pale'][6], 0.5)
    core.glint(cv, 13, 13, arm=1)
    return cv.image()


def _brazier_side(lit):
    """Blackstone bowl wall with two dark iron bands and rivets; lit, the seam under the rim glows."""
    rng = core.rng_for('ember_brazier_side')
    cv = surface('mute', S, S, rng, face='side', bevel=0, wear=0.35, tile=False, base_shift=-1.2)
    for y0 in (3, 22):
        cv.part(m_rect(S, S, 0, y0, S - 1, y0 + 3), 'iron', 'flat', 1)
        cv.shade_px(m_rect(S, S, 0, y0, S - 1, y0), 0.3, toward=(230, 230, 240))
        for x in (3, 15, 27):
            cv.px(x, y0 + 1, RAMPS['iron'][5])
    cv.shade_px(m_rect(S, S, 0, 0, S - 1, 0) | m_rect(S, S, 0, 0, 0, S - 1), 0.25, toward=(240, 240, 250))
    cv.shade_px(m_rect(S, S, 0, S - 1, S - 1, S - 1) | m_rect(S, S, S - 1, 0, S - 1, S - 1), 0.4)
    # vents between the bands
    for x in (7, 15, 23):
        vent = m_rect(S, S, x, 11, x + 1, 15)
        cv.fill(vent, RAMPS['ember'][4] if lit else RAMPS['deep'][0])
        if lit:
            cv.px(x, 12, RAMPS['ember'][5])
    if lit:
        cv.fill(m_rect(S, S, 0, 0, S - 1, 1), RAMPS['ember'][3])
        cv.fill(m_rect(S, S, 0, 0, S - 1, 0), RAMPS['ember'][5])
    return cv.image()


def brazier_side():
    return _brazier_side(False)


def brazier_side_lit():
    return _brazier_side(True)


def _brazier_top(lit):
    """An iron grate over the bowl: cold ash and charcoal, or embers glowing between the bars."""
    rng = core.rng_for('ember_brazier_top')
    cv = surface('mute', S, S, rng, face='top', bevel=0, wear=0.3, tile=False, base_shift=-1.0)
    bowl = m_rect(S, S, 3, 3, S - 4, S - 4)
    if lit:
        cv.part(bowl, 'ember', 'flat', 3, rng=rng, noise=0.5)
        shapes.glow(cv, 15.5, 15.5, 11, RAMPS['ember'][5], 0.45)
        for _ in range(14):
            x, y = rng.randrange(4, S - 4), rng.randrange(4, S - 4)
            cv.px(x, y, RAMPS['ember'][6])
    else:
        cv.part(bowl, 'mute', 'flat', 1, rng=rng, noise=0.5)
        for _ in range(12):
            x, y = rng.randrange(4, S - 4), rng.randrange(4, S - 4)
            cv.px(x, y, RAMPS['mute'][4])
    cv.shade_px(m_rect(S, S, 3, 3, S - 4, 3) | m_rect(S, S, 3, 3, 3, S - 4), 0.5)
    grate = m_rect(S, S, 3, 0, 3, 0)
    for k in range(4, S - 4, 6):
        grate = grate | m_rect(S, S, k + 2, 3, k + 3, S - 4)
    grate = grate | m_rect(S, S, 3, 15, S - 4, 16)
    cv.part(grate & bowl, 'iron', 'flat', 1)
    cv.shade_px(m_rect(S, S, 0, 0, S - 1, 0) | m_rect(S, S, 0, 0, 0, S - 1), 0.25, toward=(240, 240, 250))
    cv.shade_px(m_rect(S, S, 0, S - 1, S - 1, S - 1) | m_rect(S, S, S - 1, 0, S - 1, S - 1), 0.4)
    return cv.image()


def brazier_top():
    return _brazier_top(False)


def brazier_top_lit():
    return _brazier_top(True)


def brazier_bottom():
    rng = core.rng_for('ember_brazier_bottom')
    cv = surface('mute', S, S, rng, face='bottom', bevel=1, wear=0.3, tile=False, base_shift=-1.2)
    return cv.image()


def _loom_side(threaded):
    """A purpur loom frame: two posts and a cloth beam around a lilac back, pale warp threads strung top to bottom;
    threaded, a woven band of pale cloth grows up from the lower beam and a shuttle line crosses the warp."""
    rng = core.rng_for('hush_loom_side')
    cv = surface('lilac', S, S, rng, face='side', bevel=0, wear=0.3, tile=False, base_shift=-0.2)
    back = m_rect(S, S, 4, 6, S - 5, S - 7)
    cv.fill(back, RAMPS['lilac'][2])
    cv.shade_px(m_rect(S, S, 4, 6, S - 5, 7), 0.35)
    for x0 in (1, S - 5):
        cv.part(m_rect(S, S, x0, 1, x0 + 3, S - 2), 'amethyst', 'flat', 1)
        cv.fill(m_rect(S, S, x0 + 1, 1, x0 + 1, S - 2), RAMPS['magenta'][4])
        cv.shade_px(m_rect(S, S, x0 + 3, 1, x0 + 3, S - 2), 0.35)
    for y0 in (1, S - 6):
        cv.part(m_rect(S, S, 1, y0, S - 2, y0 + 4), 'amethyst', 'flat', 1)
        cv.shade_px(m_rect(S, S, 1, y0, S - 2, y0), 0.3, toward=(250, 230, 250))
    # warp: always strung, pale; brighter on the threaded loom
    for x in range(6, S - 6, 2):
        cv.fill(m_rect(S, S, x, 6, x, S - 7), RAMPS['white'][6] if threaded else RAMPS['pale'][5])
    # heddle bar across the warp
    cv.fill(m_rect(S, S, 4, 11, S - 5, 11), RAMPS['amethyst'][3])
    if threaded:
        cloth = m_rect(S, S, 5, 19, S - 6, S - 7)
        cv.fill(cloth, RAMPS['pale'][5])
        for y in range(19, S - 6, 2):
            cv.fill(m_rect(S, S, 5, y, S - 6, y) & cloth, RAMPS['white'][6])
        cv.fill(m_rect(S, S, 5, 17, S - 6, 17), RAMPS['lilac'][5])
        cv.px(6, 17, RAMPS['magenta'][4])
        cv.px(S - 7, 17, RAMPS['magenta'][4])
    cv.shade_px(m_rect(S, S, 0, S - 1, S - 1, S - 1) | m_rect(S, S, S - 1, 0, S - 1, S - 1), 0.4)
    return cv.image()


def loom_side():
    return _loom_side(False)


def loom_side_threaded():
    return _loom_side(True)


def _loom_top(threaded):
    """The loom from above: a purpur frame, the warp threads running across a lilac well over the cloth beam;
    threaded, a pale woven square fills the middle."""
    rng = core.rng_for('hush_loom_top')
    cv = surface('amethyst', S, S, rng, face='top', bevel=0, wear=0.3, tile=False, base_shift=0.3)
    well = m_rect(S, S, 5, 5, S - 6, S - 6)
    cv.fill(well, RAMPS['lilac'][2])
    for k in range(6, S - 6, 2):
        cv.fill(m_rect(S, S, k, 5, k, S - 6) & well, RAMPS['white'][6] if threaded else RAMPS['pale'][5])
    cv.fill(m_rect(S, S, 5, 15, S - 6, 16), RAMPS['amethyst'][3])
    if threaded:
        cloth = m_rect(S, S, 8, 8, S - 9, S - 9)
        cv.fill(cloth, RAMPS['pale'][5])
        for k in range(8, S - 8, 2):
            cv.fill(m_rect(S, S, 8, k, S - 9, k) & cloth, RAMPS['white'][6])
        core.glint(cv, 12, 12, arm=1)
    cv.shade_px(m_rect(S, S, 5, 5, S - 6, 5) | m_rect(S, S, 5, 5, 5, S - 6), 0.45)
    cv.shade_px(m_rect(S, S, 0, 0, S - 1, 0) | m_rect(S, S, 0, 0, 0, S - 1), 0.25, toward=(250, 230, 250))
    cv.shade_px(m_rect(S, S, 0, S - 1, S - 1, S - 1) | m_rect(S, S, S - 1, 0, S - 1, S - 1), 0.4)
    return cv.image()


def loom_top():
    return _loom_top(False)


def loom_top_threaded():
    return _loom_top(True)


def loom_bottom():
    rng = core.rng_for('hush_loom_bottom')
    cv = surface('amethyst', S, S, rng, face='bottom', bevel=1, wear=0.3, tile=False, base_shift=-0.2)
    return cv.image()


def _memorial_base(seed):
    rng = core.rng_for(seed)
    cv = surface('pale', S, S, rng, face='side', bevel=0, wear=0.25, tile=False, base_shift=0.2)
    cv.shade_px(m_rect(S, S, 0, 0, S - 1, 0) | m_rect(S, S, 0, 0, 0, S - 1), 0.25, toward=(255, 255, 255))
    cv.shade_px(m_rect(S, S, 0, S - 1, S - 1, S - 1) | m_rect(S, S, S - 1, 0, S - 1, S - 1), 0.4)
    return cv, rng


def memorial_side():
    """Pale stone over the old Scar: a healed crack, its violet sealed by a seam of forget-me-not blue."""
    cv, rng = _memorial_base('memorial_stone_side')
    pts = [(9, 0), (13, 7), (11, 13), (17, 19), (14, 25), (19, 31)]
    cv.fill(core.m_lines(S, S, pts, 3), RAMPS['scar'][3])
    cv.fill(core.m_lines(S, S, pts, 1), RAMPS['recollite'][6])
    # a carved band near the top, like a name line
    cv.shade_px(m_rect(S, S, 4, 5, S - 5, 6), 0.35)
    cv.shade_px(m_rect(S, S, 4, 7, S - 5, 7), 0.2, toward=(255, 255, 255))
    for (x, y) in ((6, 22), (25, 10)):
        cv.fill(m_ellipse(S, S, x + 0.5, y + 0.5, 1.4, 1.4), RAMPS['recollite'][5])
        cv.px(x, y, RAMPS['pale'][6])
    return cv.image()


def memorial_top():
    """The memorial's cap: a ring of carved stone around a small forget-me-not inlay."""
    cv, rng = _memorial_base('memorial_stone_top')
    ring = m_ellipse(S, S, 15.5, 15.5, 9, 9) & ~m_ellipse(S, S, 15.5, 15.5, 7.5, 7.5)
    cv.shade_px(ring, 0.35)
    for k in range(5):
        a = k * 2 * math.pi / 5
        x, y = 15.5 + math.cos(a) * 3, 15.5 + math.sin(a) * 3
        cv.fill(m_ellipse(S, S, x, y, 1.8, 1.8), RAMPS['recollite'][5])
    cv.fill(m_ellipse(S, S, 15.5, 15.5, 1.2, 1.2), RAMPS['pale'][6])
    shapes.glow(cv, 15.5, 15.5, 6, RAMPS['pale'][6], 0.35)
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


def remembrance_posy():
    """A small bunch of forget-me-nots, stems gathered and tied with a pale string bow."""
    cv = Canvas(S, S)
    for a, b in (((13, 30), (12, 15)), ((15, 30), (19, 10)), ((14, 30), (7, 11)), ((16, 30), (24, 15)), ((14, 30), (15, 8))):
        cv.fill(core.m_line(S, S, a, b, 1), RAMPS['verdigris'][2])
    for (x, y, w) in ((9, 17, 2.2), (21, 19, 2.0)):
        cv.fill(m_ellipse(S, S, x + 0.5, y + 0.5, w, 1.1), RAMPS['verdigris'][4])
    # the string: a band and a small bow
    cv.fill(m_rect(S, S, 12, 23, 17, 24), RAMPS['white'][4])
    cv.fill(m_ellipse(S, S, 11.5, 22.5, 1.6, 1.2) | m_ellipse(S, S, 18.5, 22.5, 1.6, 1.2), RAMPS['white'][5])
    cv.fill(core.m_line(S, S, (14, 25), (12, 28), 1) | core.m_line(S, S, (16, 25), (18, 28), 1), RAMPS['white'][3])
    for cx, cy in ((12, 14), (19, 9), (7, 10), (24, 14), (15, 7), (10, 6), (21, 4)):
        for k in range(5):
            ang = k * 2 * math.pi / 5 - math.pi / 2
            px, py = cx + math.cos(ang) * 1.8, cy + math.sin(ang) * 1.8
            petal = m_ellipse(S, S, px + 0.5, py + 0.5, 1.4, 1.4)
            cv.fill(petal, mix(RAMPS['indigo'][6], RAMPS['glass'][5], 0.35) if k in (0, 4) else mix(RAMPS['indigo'][5], RAMPS['glass'][4], 0.3))
        cv.px(cx, cy, RAMPS['brass'][5])
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


def recollite_lens():
    """The chronicle lens with recollite glass and three recollite chips set into the rim."""
    from . import items
    cv = items.chronicle_lens(glass='recollite', seed='recollite_lens', canvas=True)
    for (x, y) in ((19, 3), (28, 13), (11, 15)):
        cv.fill(m_ellipse(S, S, x + 0.5, y + 0.5, 1.3, 1.3), RAMPS['recollite'][5])
        cv.px(x, y, RAMPS['pale'][6])
    cv.outline(selout=0.55)
    return cv.image()


def recollite_needle():
    """The extraction needle with a recollite tip and a recollite-wound grip band."""
    from . import items
    cv = items.extraction_needle(tip='recollite', seed='recollite_needle', canvas=True)
    for (x, y) in ((8, 24), (9, 23)):
        cv.px(x, y, RAMPS['recollite'][4])
    cv.fill(m_ellipse(S, S, 22.5, 9.5, 1.2, 1.2), RAMPS['recollite'][5])
    cv.px(22, 9, RAMPS['pale'][6])
    cv.outline(selout=0.55)
    return cv.image()


def recollite_pickaxe():
    """An iron-tier pickaxe: a wooden haft, an iron collar and a curved recollite head with a pale seam along its edge."""
    rng = core.rng_for('recollite_pickaxe')
    cv = Canvas(S, S)
    shapes.rod(cv, (4, 29), (19, 14), 3, 'wood', 3, rng=rng, noise=0.3)
    head = shapes.gem(cv, [(4, 9), (9, 4.5), (16, 3), (23, 5), (27.5, 9), (29.5, 15), (28, 23), (25, 17), (22.5, 12.5),
                           (18, 9.5), (12, 8.5), (7, 10.5)], 'recollite', 4)
    seam = core.m_lines(S, S, [(6, 8), (11, 5.5), (17, 4.5), (23, 6.5), (27, 10)], 1) & head
    cv.fill(seam, RAMPS['pale'][6])
    shapes.rod(cv, (16, 13), (20, 9), 4.5, 'iron', 3)
    cv.px(18, 10, RAMPS['iron'][6])
    shapes.glow(cv, 17, 7, 8, RAMPS['recollite'][6], 0.25)
    core.glint(cv, 26, 10, arm=0)
    cv.outline(selout=0.55)
    return cv.image()


def hollow_effigy():
    """A small hollowstone figure on a brick plinth: a hooded body with a hollow face, and a recollite heart set in its
    chest that glows through the stone."""
    rng = core.rng_for('hollow_effigy')
    cv = Canvas(S, S)
    stone = RAMPS['pale']
    # plinth: two courses of hollowstone brick
    cv.fill(m_rect(S, S, 8, 25, 23, 29), stone[2])
    cv.fill(m_rect(S, S, 8, 25, 23, 25), stone[3])
    for x in (12, 18):
        cv.px(x, 27, stone[1])
    cv.fill(m_rect(S, S, 8, 27, 23, 27), stone[1])
    cv.fill(m_rect(S, S, 15, 28, 15, 29), stone[1])
    # body: a hooded robe narrowing to the shoulders
    robe = m_rect(S, S, 12, 13, 19, 24) | m_rect(S, S, 11, 18, 20, 24)
    cv.fill(robe, stone[4])
    for x in range(12, 20, 2):
        cv.fill(m_rect(S, S, x, 16, x, 24) & robe, stone[3])
    cv.fill(m_rect(S, S, 19, 13, 20, 24) & robe, stone[2])
    # head and hood
    head = m_ellipse(S, S, 16.0, 9.0, 4.2, 4.6)
    cv.fill(head, stone[5])
    cv.fill(m_rect(S, S, 14, 8, 18, 12) | m_rect(S, S, 15, 7, 17, 13), RAMPS['indigo'][1])
    for x in (15, 17):
        cv.px(x, 9, RAMPS['recollite'][6])
        cv.px(x, 10, RAMPS['recollite'][4])
    # the recollite heart, glowing through the stone
    shapes.glow(cv, 16, 18, 5, RAMPS['recollite'][5], 0.45)
    shapes.gem(cv, [(15, 16), (17, 16), (18, 18), (16, 21), (14, 18)], 'recollite', 4)
    cv.px(16, 17, RAMPS['pale'][6])
    # a few chips worn out of the stone
    for _ in range(4):
        x, y = rng.randrange(12, 20), rng.randrange(14, 24)
        cv.px(x, y, stone[2])
    cv.outline(selout=0.5)
    return cv.image()


def cinderkin():
    """The cinderkin on the faded's layout: charcoal skin and ash-dark cloth with glowing ember cracks, ember eyes."""
    return _faded_sheet('cinderkin', 'mute', 'deep', crown=False, cracks=True)


def memory_ember():
    """A lump of charcoal split open, a hot ember core glowing in the crack, a faint pale wisp (the memory) rising."""
    rng = core.rng_for('memory_ember')
    cv = Canvas(S, S)
    lump = m_poly(S, S, [(7, 21), (10, 14), (16, 11), (23, 13), (26, 19), (23, 26), (15, 28), (9, 26)])
    shapes.glow(cv, 16, 20, 12, RAMPS['ember'][4], 0.3)
    cv.fill(lump, RAMPS['mute'][1])
    cv.fill(lump & m_rect(S, S, 0, 0, S - 1, 17), RAMPS['mute'][3])
    crack = core.m_lines(S, S, [(10, 23), (14, 19), (17, 21), (21, 16), (24, 18)], 1) & lump
    cv.fill(crack, RAMPS['ember'][4])
    cv.fill(erode(lump) & m_ellipse(S, S, 17.0, 20.0, 3.0, 2.2), RAMPS['ember'][5])
    for _ in range(5):
        x, y = rng.randrange(10, 23), rng.randrange(15, 26)
        if lump[y, x]:
            cv.px(x, y, RAMPS['ember'][3])
    # the memory leaving: a thin pale wisp
    cv.fill(core.m_lines(S, S, [(17, 11), (15, 8), (17, 5), (16, 2)], 1), mix(RAMPS['pale'][5], RAMPS['ember'][5], 0.25))
    cv.outline(selout=0.5)
    return cv.image()


def cinderkin_spawn_egg():
    from .items import _egg
    return _egg('cinderkin_spawn_egg', 'mute', 'ember', 5, band='ember')


def hushling():
    """The hushling on the faded's layout: bone-white skin and pale cloth; its own face carries a dark open mouth."""
    img = _faded_sheet('hushling', 'bone', 'paper', crown=False)
    # the mouth: a dark oval low on the face (head front is at x 8..15, y 8..15 of the 64x64 sheet, scaled by 2)
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    k = img.width // 64
    d.ellipse([11 * k, 13 * k + k // 2, 13 * k - 1, 15 * k - 1], fill=(*RAMPS['deep'][0], 255))
    return img


def hush_filament():
    """A loose pale thread looped once, fading to nothing at both ends."""
    cv = Canvas(S, S)
    pts = [(4, 26), (9, 20), (15, 18), (21, 12), (24, 7), (20, 5), (16, 9), (17, 15), (22, 20), (27, 23)]
    cv.fill(core.m_lines(S, S, pts, 1), RAMPS['bone'][5])
    cv.fill(core.m_lines(S, S, pts[2:8], 1), RAMPS['white'][6])
    for x, y in ((4, 26), (27, 23)):
        cv.px(x, y, mix(RAMPS['bone'][4], RAMPS['lilac'][3], 0.5))
    core.glint(cv, 21, 12, arm=1)
    cv.outline(selout=0.5)
    return cv.image()


def tide_lure():
    """A prismarine fish-shaped lure with a recollite eye and a short string loop."""
    cv = Canvas(S, S)
    body = core.m_ellipse(S, S, 15.5, 18.5, 9, 5)
    cv.fill(body, RAMPS['verdigris'][4])
    cv.fill(core.m_ellipse(S, S, 15.5, 17.5, 8, 3), RAMPS['verdigris'][5])
    tail = core.m_lines(S, S, [(24, 18), (28, 14), (28, 23), (24, 18)], 1)
    cv.fill(tail, RAMPS['verdigris'][3])
    cv.px(10, 17, RAMPS['deep'][0])
    cv.px(11, 17, RAMPS['recollite'][5])
    cv.fill(core.m_lines(S, S, [(8, 16), (5, 10), (8, 5)], 1), RAMPS['white'][6])
    core.glint(cv, 14, 16, arm=1)
    cv.outline(selout=0.5)
    return cv.image()


def hushling_spawn_egg():
    from .items import _egg
    return _egg('hushling_spawn_egg', 'bone', 'deep', 5, band='lilac')


def lethe_tear():
    """A single tear of pale recollite glass, a darker memory curled inside it, a bright glint at the shoulder."""
    cv = Canvas(S, S)
    drop = m_ellipse(S, S, 16.0, 20.0, 7.5, 8.0) | m_poly(S, S, [(16, 3), (22.5, 17), (9.5, 17)])
    shapes.glow(cv, 16, 19, 12, RAMPS['recollite'][4], 0.25)
    cv.fill(drop, RAMPS['recollite'][4])
    cv.fill(drop & m_ellipse(S, S, 18.0, 22.0, 6.0, 6.0), RAMPS['recollite'][3])
    cv.fill(drop & m_ellipse(S, S, 19.0, 24.0, 4.0, 3.5), RAMPS['recollite'][2])
    # a curl of forgetting inside: a small dark spiral
    cv.fill(core.m_lines(S, S, [(14, 22), (16, 19), (19, 20), (18, 24), (14, 25)], 1) & drop, RAMPS['indigo'][2])
    # light on the left shoulder
    cv.fill(drop & m_ellipse(S, S, 12.5, 16.5, 2.0, 3.5), RAMPS['pale'][5])
    core.glint(cv, 12, 14, arm=1)
    cv.outline(selout=0.55)
    return cv.image()


def remembering_circlet():
    """A thin gold circlet seen from the front: the band in an ellipse, three recollite stones, a forget-me-not at the brow."""
    cv = Canvas(S, S)
    outer = m_ellipse(S, S, 16.0, 17.0, 13.0, 7.0)
    inner = m_ellipse(S, S, 16.0, 16.0, 10.5, 4.6)
    band = outer & ~inner
    cv.fill(band, RAMPS['gold'][4])
    # back of the band darker, front lit
    cv.fill(band & m_rect(S, S, 0, 0, S - 1, 15), RAMPS['gold'][2])
    cv.fill(band & m_rect(S, S, 0, 21, S - 1, S - 1), RAMPS['gold'][5])
    for x, y in ((8, 20), (24, 20)):
        cv.fill(m_ellipse(S, S, x + 0.5, y + 0.5, 1.6, 1.6), RAMPS['recollite'][4])
        cv.px(x, y, RAMPS['pale'][6])
    # the brow stone: a forget-me-not over a recollite drop
    shapes.glow(cv, 16, 22, 5, RAMPS['recollite'][5], 0.35)
    for k in range(5):
        ang = k * 2 * math.pi / 5 - math.pi / 2
        cv.fill(m_ellipse(S, S, 16.5 + math.cos(ang) * 2.0, 22.5 + math.sin(ang) * 2.0, 1.4, 1.4),
                mix(RAMPS['indigo'][5], RAMPS['glass'][4], 0.3))
    cv.px(16, 22, RAMPS['brass'][5])
    cv.outline(selout=0.5)
    return cv.image()


def circlet_worn():
    """The circlet on the player's head (64x32 armor UV, stored 128x64): a gold band round the brow, recollite stones
    at the sides and a forget-me-not stone at the front; everything else stays clear."""
    from .armory import ArmorSheet
    sheet = ArmorSheet('remembering_circlet_worn')

    def head(f, w, h, rng):
        if f in ('up', 'down'):
            return None
        cv = Canvas(w, h)
        y0 = h // 2 - 3
        cv.fill(m_rect(w, h, 0, y0, w - 1, y0 + 2), RAMPS['gold'][4])
        cv.fill(m_rect(w, h, 0, y0, w - 1, y0), RAMPS['gold'][5])
        cv.fill(m_rect(w, h, 0, y0 + 2, w - 1, y0 + 2), RAMPS['gold'][2])
        if f == 'north':
            cx = w // 2
            cv.fill(m_rect(w, h, cx - 2, y0 - 2, cx + 1, y0 + 3), RAMPS['indigo'][5])
            cv.fill(m_rect(w, h, cx - 1, y0 - 1, cx, y0 + 2), RAMPS['glass'][5])
            cv.px(cx - 1, y0, RAMPS['brass'][5])
        elif f in ('left', 'right'):
            cx = w // 2
            cv.fill(m_rect(w, h, cx - 1, y0, cx, y0 + 2), RAMPS['recollite'][4])
            cv.px(cx - 1, y0, RAMPS['pale'][6])
        return cv
    sheet.box(0, 0, 8, 8, 8, head)
    return sheet.image()


def faded_spawn_egg():
    from .items import _egg
    return _egg('faded_spawn_egg', 'pale', 'lilac', 3, band='recollite')


def faded():
    """The faded's 64×64 layout (FadedModel): pale worn cloth over a body that frays into a veil, a hollow face,
    a recollite splinter at the heart. Painted mostly opaque; the renderer makes the whole figure translucent."""
    return _faded_sheet('faded', 'pale', 'lilac', crown=False)


def unremembered():
    """The Unremembered on the faded's layout: the same figure in dusk-dark cloth (indigo over lilac skin), the face sockets
    lit recollite, and a crown of recollite splinters standing round the hood. Drawn 1.8x by its renderer."""
    return _faded_sheet('unremembered', 'lilac', 'indigo', crown=True)


def _faded_sheet(name, skin_mat, cloth_mat, crown, cracks=False):
    from .entities import Sheet
    from .materials import surface
    s = Sheet(name)
    hi = 'pale' if not crown else 'recollite'

    def fray(cv, rng, rows):
        h, w = cv.alpha.shape
        for x in range(w):
            cut = rng.randrange(0, rows + 1)
            if cut:
                cv.alpha[h - cut:, x] = 0

    def head(f, w, h, rng):
        cv = surface(skin_mat, w, h, rng, face='top' if f == 'up' else 'side', wear=0.45, base_shift=0.3)
        if f == 'north':
            # a hollow face: two soft dark sockets and a faint mouth line, no features
            for x0 in (w // 4 - 1, w - w // 4 - 3):
                cv.fill(m_rect(w, h, x0, h // 2 - 3, x0 + 3, h // 2 + 1), RAMPS['ink'][1])
                cv.px(x0 + 1, h // 2 - 2, RAMPS['recollite'][5])
                if crown:
                    cv.px(x0 + 2, h // 2 - 2, RAMPS['recollite'][6])
                    cv.px(x0 + 1, h // 2 - 1, RAMPS['recollite'][4])
                # the face runs: a pale streak under each socket
                cv.fill(m_rect(w, h, x0 + 1, h // 2 + 2, x0 + 2, h - 3), RAMPS['lilac'][3])
            cv.fill(m_rect(w, h, w // 2 - 2, h - 3, w // 2 + 1, h - 3), RAMPS['lilac'][2])
        if f in ('up', 'north', 'left', 'right', 'south'):
            # a pale hood edge
            cv.fill(m_rect(w, h, 0, 0, w - 1, 1), RAMPS['lilac'][4] if not crown else RAMPS['indigo'][4])
        if crown and f in ('north', 'left', 'right', 'south'):
            # the splinter crown: recollite shards standing on the hood rim, each with a lit tip
            for x in range(1, w - 1, 3):
                tall = 2 + (x * 7 % 3)
                cv.fill(m_rect(w, h, x, 0, x, tall), RAMPS['recollite'][4])
                cv.px(x, 0, RAMPS['pale'][6])
        if crown and f == 'up':
            for x in range(0, w, 3):
                cv.px(x, 0, RAMPS['recollite'][5])
                cv.px(x, h - 1, RAMPS['recollite'][5])
        return cv

    def body(f, w, h, rng):
        cv = surface(cloth_mat, w, h, rng, face='side', wear=0.55, base_shift=0.6)
        if f in ('north', 'south', 'left', 'right'):
            for x in range(1, w, 3):
                cv.shade_px(m_rect(w, h, x, 0, x, h - 1), 0.22)
            cv.fill(m_rect(w, h, 0, 4, w - 1, 4), RAMPS[hi][5])
        return cv

    def veil(f, w, h, rng):
        if f in ('up', 'down'):
            return None
        cv = surface(cloth_mat, w, h, rng, face='side', wear=0.6, base_shift=0.9)
        for x in range(0, w, 2):
            cv.shade_px(m_rect(w, h, x, 0, x, h - 1), 0.18)
        fray(cv, rng, h // 2)
        return cv

    def arm(f, w, h, rng):
        cv = surface(skin_mat, w, h, rng, face='side', wear=0.5, base_shift=0.1)
        if f in ('north', 'south', 'left', 'right'):
            cv.fill(m_rect(w, h, 0, 0, w - 1, 9), RAMPS[cloth_mat][3])
            fray(cv, rng, 3)
        return cv

    def leg(f, w, h, rng):
        cv = surface(skin_mat, w, h, rng, face='side', wear=0.55, base_shift=-0.2)
        fray(cv, rng, 6)
        return cv

    def splinter(f, w, h, rng):
        cv = surface('recollite', w, h, rng, wear=0.0, base_shift=0.8)
        cv.px(0, 0, RAMPS['pale'][6])
        return cv

    def cracked(fn):
        """Glowing ember cracks over a charcoal face: a few short jagged runs, hot core, cooler rim."""
        if not cracks:
            return fn

        def paint(f, w, h, rng):
            cv = fn(f, w, h, rng)
            if cv is None or f in ('up', 'down'):
                return cv
            for _ in range(max(1, (w * h) // 90)):
                x, y = rng.randrange(w), rng.randrange(h)
                for _ in range(rng.randrange(3, 7)):
                    if 0 <= x < w and 0 <= y < h and cv.alpha[y, x] > 0:
                        cv.px(x, y, RAMPS['ember'][4])
                        if x + 1 < w and cv.alpha[y, x + 1] > 0:
                            cv.px(x + 1, y, RAMPS['ember'][2])
                    x += rng.choice((-1, 0, 1))
                    y += 1
            return cv
        return paint

    s.box(0, 0, 8, 8, 8, cracked(head))
    s.box(16, 16, 8, 12, 4, cracked(body))
    s.box(0, 32, 9, 9, 5, cracked(veil))
    s.box(40, 16, 3, 12, 3, cracked(arm))
    s.box(0, 16, 3, 12, 3, cracked(leg))
    s.box(56, 0, 2, 3, 1, splinter)
    return s.image()


BLOCK_TEXTURES = {
    'hollow_turf_top': turf_top,
    'hollow_turf_side': turf_side,
    'hollowstone': hollowstone,
    'hollowstone_bricks': hollowstone_bricks,
    'recollite_ore': recollite_ore,
    'recollite_block': recollite_block,
    'recollite_lantern_side': lantern_side,
    'recollite_lantern_top': lantern_top,
    'ember_brazier_side': brazier_side,
    'ember_brazier_side_lit': brazier_side_lit,
    'ember_brazier_top': brazier_top,
    'ember_brazier_top_lit': brazier_top_lit,
    'ember_brazier_bottom': brazier_bottom,
    'hush_loom_side': loom_side,
    'hush_loom_side_threaded': loom_side_threaded,
    'hush_loom_top': loom_top,
    'hush_loom_top_threaded': loom_top_threaded,
    'hush_loom_bottom': loom_bottom,
    'memorial_stone_side': memorial_side,
    'memorial_stone_top': memorial_top,
    'forget_me_not': forget_me_not,
}
ITEM_TEXTURES = {
    'recollite_shard': recollite_shard,
    'recollite_lens': recollite_lens,
    'recollite_needle': recollite_needle,
    'recollite_pickaxe': recollite_pickaxe,
    'remembrance_posy': remembrance_posy,
    'hollow_effigy': hollow_effigy,
    'lethe_tear': lethe_tear,
    'memory_ember': memory_ember,
    'hush_filament': hush_filament,
    'tide_lure': tide_lure,
    'hushling_spawn_egg': hushling_spawn_egg,
    'cinderkin_spawn_egg': cinderkin_spawn_egg,
    'remembering_circlet': remembering_circlet,
    'faded_spawn_egg': faded_spawn_egg,
}
EQUIPMENT_TEXTURES = {
    'remembering_circlet': circlet_worn,
}
ENTITY_TEXTURES = {
    'faded': faded,
    'unremembered': unremembered,
    'cinderkin': cinderkin,
    'hushling': hushling,
}
