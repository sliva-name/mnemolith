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


def faded_spawn_egg():
    from .items import _egg
    return _egg('faded_spawn_egg', 'pale', 'lilac', 3, band='recollite')


def faded():
    """The faded's 64×64 layout (FadedModel): pale worn cloth over a body that frays into a veil, a hollow face,
    a recollite splinter at the heart. Painted mostly opaque; the renderer makes the whole figure translucent."""
    import numpy as np
    from .entities import Sheet
    from .materials import surface
    s = Sheet('faded')

    def fray(cv, rng, rows):
        h, w = cv.alpha.shape
        for x in range(w):
            cut = rng.randrange(0, rows + 1)
            if cut:
                cv.alpha[h - cut:, x] = 0

    def head(f, w, h, rng):
        cv = surface('pale', w, h, rng, face='top' if f == 'up' else 'side', wear=0.45, base_shift=0.3)
        if f == 'north':
            # a hollow face: two soft dark sockets and a faint mouth line, no features
            for x0 in (w // 4 - 1, w - w // 4 - 3):
                cv.fill(m_rect(w, h, x0, h // 2 - 3, x0 + 3, h // 2 + 1), RAMPS['ink'][1])
                cv.px(x0 + 1, h // 2 - 2, RAMPS['recollite'][5])
                # the face runs: a pale streak under each socket
                cv.fill(m_rect(w, h, x0 + 1, h // 2 + 2, x0 + 2, h - 3), RAMPS['lilac'][3])
            cv.fill(m_rect(w, h, w // 2 - 2, h - 3, w // 2 + 1, h - 3), RAMPS['lilac'][2])
        if f in ('up', 'north', 'left', 'right', 'south'):
            # a pale hood edge
            cv.fill(m_rect(w, h, 0, 0, w - 1, 1), RAMPS['lilac'][4])
        return cv

    def body(f, w, h, rng):
        cv = surface('lilac', w, h, rng, face='side', wear=0.55, base_shift=0.6)
        if f in ('north', 'south', 'left', 'right'):
            for x in range(1, w, 3):
                cv.shade_px(m_rect(w, h, x, 0, x, h - 1), 0.22)
            cv.fill(m_rect(w, h, 0, 4, w - 1, 4), RAMPS['pale'][5])
        return cv

    def veil(f, w, h, rng):
        if f in ('up', 'down'):
            return None
        cv = surface('lilac', w, h, rng, face='side', wear=0.6, base_shift=0.9)
        for x in range(0, w, 2):
            cv.shade_px(m_rect(w, h, x, 0, x, h - 1), 0.18)
        fray(cv, rng, h // 2)
        return cv

    def arm(f, w, h, rng):
        cv = surface('pale', w, h, rng, face='side', wear=0.5, base_shift=0.1)
        if f in ('north', 'south', 'left', 'right'):
            cv.fill(m_rect(w, h, 0, 0, w - 1, 9), RAMPS['lilac'][3])
            fray(cv, rng, 3)
        return cv

    def leg(f, w, h, rng):
        cv = surface('pale', w, h, rng, face='side', wear=0.55, base_shift=-0.2)
        fray(cv, rng, 6)
        return cv

    def splinter(f, w, h, rng):
        cv = surface('recollite', w, h, rng, wear=0.0, base_shift=0.8)
        cv.px(0, 0, RAMPS['pale'][6])
        return cv

    s.box(0, 0, 8, 8, 8, head)
    s.box(16, 16, 8, 12, 4, body)
    s.box(0, 32, 9, 9, 5, veil)
    s.box(40, 16, 3, 12, 3, arm)
    s.box(0, 16, 3, 12, 3, leg)
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
    'faded_spawn_egg': faded_spawn_egg,
}
ENTITY_TEXTURES = {
    'faded': faded,
}
