"""Blocks: element models plus their 64x64 sheets (2 texels per model unit), and the two cube textures."""
import numpy as np

from . import core, shapes
from .core import Canvas, m_rect, m_ellipse, m_poly, m_line, m_ring, dilate, erode
from .materials import surface, rivet, inset, raised
from .modelgen import Model, F, P, BLOCK_DISPLAY
from .palette import RAMPS, mix, OUTLINE, TEMPER


def mat(name, w, h, rng, **kw):
    return surface(name, w, h, rng, **kw)


# ------------------------------------------------------------------ archival stratum

def _strata(w, h, rng, top_band=None):
    cv = mat('navy', w, h, rng, bevel=0, wear=0.3)
    off = core.value_noise(w, 1, 4, rng, wrap=True)[0]
    for yb, ramp, i in ((5, 'bone', 3), (13, 'verdigris', 2), (25, 'bone', 2), (29, 'navy', 1)):
        for x in range(w):
            y = int(yb + (off[x] - 0.5) * 2)
            if 0 <= y < h:
                cv.px(x, y, RAMPS[ramp][i])
                if y + 1 < h and ramp != 'navy':
                    cv.px(x, y + 1, RAMPS['navy'][1])
    for _ in range(w * h // 70):   # fossil imprints: tiny lit flecks
        x, y = rng.randrange(w), rng.randrange(h)
        cv.px(x, y, rng.choice((RAMPS['verdigris'][5], RAMPS['echo'][4], RAMPS['bone'][5])))
    return cv


def stratum_core_side(w, h, rng):
    cv = _strata(w, h, rng)
    cv.shade_px(m_rect(w, h, w - 1, 0, w - 1, h - 1), 0.25)
    cv.shade_px(m_rect(w, h, 0, 0, 0, h - 1), 0.2, toward=(255, 250, 235))
    return cv


def stratum_band_side(w, h, rng):
    cv = mat('bone', w, h, rng, wear=0.5, base_shift=-0.8)
    for x in range(w):
        if rng.random() < 0.5:
            cv.px(x, h // 2, RAMPS['verdigris'][3])
    for x in range(0, w, 7):
        cv.px(x + rng.randrange(3), rng.randrange(1, h - 1), RAMPS['verdigris'][5])
    return cv


def stratum_top(w, h, rng):
    cv = mat('navy', w, h, rng, face='top', wear=0.3)
    ys, xs = np.mgrid[0:h, 0:w]
    rr = np.sqrt((xs + 0.5 - w / 2) ** 2 + (ys + 0.5 - h / 2) ** 2)
    ringm = (np.abs(rr - w * 0.3) < 0.6)
    cv.shade_px(ringm, 0.3, toward=RAMPS['verdigris'][4])
    for _ in range(6):
        cv.px(rng.randrange(w), rng.randrange(h), RAMPS['verdigris'][5])
    return cv


def stratum_band_top(w, h, rng):
    cv = mat('bone', w, h, rng, face='top', wear=0.5, base_shift=-0.6)
    cv.shade_px(m_rect(w, h, 3, 3, w - 4, h - 4), 0.25)
    return cv


def archival_stratum():
    m = Model('archival_stratum')
    core_side = P(stratum_core_side, 'core_side')
    m.box((2, 0, 2), (14, 16, 14), core_side, up=P(stratum_top, 'core_top'), down=P(stratum_top, 'core_top'))
    m.box((0, 5, 0), (16, 9, 16), P(stratum_band_side, 'band_side'), up=P(stratum_band_top, 'band_top'), down=P(stratum_band_top, 'band_top'))
    return m.build()


# ------------------------------------------------------------------ mute stone

def mute_plinth_side(w, h, rng):
    cv = mat('mute', w, h, rng, base_shift=-0.8, wear=0.6)
    core.grime(cv, m_rect(w, h, 0, 0, w - 1, h - 1), rng, amount=0.3, t=0.25)
    return cv


def mute_body_side(w, h, rng):
    """Dressed cobble courses (the recipe's cobblestone) with ink run down from the cap."""
    cv = mat('mute', w, h, rng, bevel=0, wear=0.3)
    y = 0
    row = 0
    while y < h:
        bh = rng.choice((4, 5, 6))
        x = -rng.randrange(0, 6) if row % 2 else 0
        while x < w:
            bw = rng.choice((6, 7, 8, 9))
            x0, x1 = max(0, x), min(w - 1, x + bw - 2)
            y1 = min(h - 1, y + bh - 2)
            if x1 > x0:
                raised(cv, x0, y, x1, y1, 0.28)
            x += bw
        cv.shade_px(m_rect(w, h, 0, min(h - 1, y + bh - 1), w - 1, min(h - 1, y + bh - 1)), 0.45)
        y += bh
        row += 1
    for _ in range(3):   # ink runs
        x = rng.randrange(1, w - 1)
        ln = rng.randint(4, h - 2)
        cv.shade_px(m_rect(w, h, x, 0, x, ln), 0.5, toward=RAMPS['ink'][1])
        cv.px(x, ln + 1, RAMPS['ink'][2])
    return cv


def mute_lip_side(w, h, rng):
    cv = mat('mute', w, h, rng, base_shift=0.4, wear=0.4)
    cv.fill(m_rect(w, h, 0, h // 2, w - 1, h // 2), RAMPS['amethyst'][2])
    for x in range(1, w, 5):
        cv.px(x, h // 2, RAMPS['amethyst'][4])
    return cv


def mute_lip_top(w, h, rng):
    cv = mat('mute', w, h, rng, face='top', wear=0.5)
    cv.shade_px(m_rect(w, h, 2, 2, w - 3, h - 3), 0.15)
    return cv


def mute_cap_top(w, h, rng):
    """The hush seal: a ring cut into the cap, inlaid with amethyst and dulled with ink, a closed mark at the centre."""
    cv = mat('mute', w, h, rng, face='top', wear=0.3, base_shift=0.3)
    cx, cy = w / 2, h / 2
    r = w * 0.38
    cv.fill(m_ring(w, h, cx, cy, r + 1, r - 1), RAMPS['mute'][1])
    cv.fill(m_ring(w, h, cx, cy, r + 0.4, r - 0.4), RAMPS['amethyst'][2])
    ys, xs = np.mgrid[0:h, 0:w]
    ang = np.arctan2(ys + 0.5 - cy, xs + 0.5 - cx)
    lit = m_ring(w, h, cx, cy, r + 0.4, r - 0.4) & (np.sin(ang * 4) > 0.3)
    cv.fill(lit, RAMPS['amethyst'][4])
    cv.fill(m_ring(w, h, cx, cy, r + 1.4, r + 1) & (ys < cy), RAMPS['mute'][5])
    # closed-eye mark: an arc and three lashes, all cut
    cv.fill(m_ring(w, h, cx, cy - 1, 4.2, 3.2) & (ys > cy - 1), RAMPS['ink'][2])
    for dx in (-3, 0, 3):
        cv.fill(m_line(w, h, (cx + dx, cy + 3), (cx + dx * 1.3, cy + 5)), RAMPS['ink'][2])
    cv.px(int(cx), int(cy - 3), RAMPS['amethyst'][5])
    return cv


def mute_stone():
    m = Model('mute_stone')
    stone_flat = F('mute', shift=-0.3, kind='top')
    m.box((1, 0, 1), (15, 3, 15), P(mute_plinth_side, 'plinth'), up=stone_flat, down=stone_flat)
    m.box((2, 3, 2), (14, 11, 14), P(mute_body_side, 'body'), skip=('up', 'down'))
    m.box((1, 11, 1), (15, 13, 15), P(mute_lip_side, 'lip'), up=P(mute_lip_top, 'lip_top'), down=stone_flat)
    m.box((3, 13, 3), (13, 15, 13), F('mute', shift=0.2), up=P(mute_cap_top, 'cap_top'), down=None)
    # iron clamps on the four body corners, where the courses meet
    for x, z in ((1.5, 1.5), (13.5, 1.5), (1.5, 13.5), (13.5, 13.5)):
        m.box((x, 5, z), (x + 1, 9, z + 1), F('dark_iron', wear=0.6), down=None)
    return m.build()


# ------------------------------------------------------------------ composition reel

def planks(w, h, rng, face='top'):
    cv = mat('wood', w, h, rng, face=face, bevel=0, wear=0.5)
    step = 6 if w >= 12 else 4
    for y in range(step - 1, h, step):
        cv.shade_px(m_rect(w, h, 0, y, w - 1, y), 0.5)
    for y0 in range(0, h, step):
        x = rng.randrange(2, max(3, w - 2))
        cv.shade_px(m_rect(w, h, x, y0, x, min(h - 1, y0 + step - 2)), 0.45)
    cv.shade_px(m_rect(w, h, 0, 0, w - 1, 0) | m_rect(w, h, 0, 0, 0, h - 1), 0.2, toward=(255, 245, 220))
    return cv


def reel_cheek(w, h, rng):
    """Copper cheek with a brass bearing around the axle and four bolts."""
    cv = mat('copper', w, h, rng, wear=0.7)
    cx, cy = w / 2, h / 2
    cv.fill(m_ring(w, h, cx, cy, 4.5, 2.5), RAMPS['brass'][3])
    cv.fill(m_ring(w, h, cx, cy, 4.5, 3.8) & (np.mgrid[0:h, 0:w][0] < cy), RAMPS['brass'][5])
    cv.fill(m_ellipse(w, h, cx, cy, 2.5, 2.5), RAMPS['iron'][1])
    for x, y in ((2, 2), (w - 4, 2), (2, h - 4), (w - 4, h - 4)):
        rivet(cv, x, y, 'iron')
    inset(cv, 1, 1, w - 2, h - 2, 0.2)
    return cv


def reel_drum(w, h, rng):
    """Paper film wound tight: frame band in echo pink, sprocket holes along both edges."""
    cv = mat('paper', w, h, rng, bevel=0, wear=0.4)
    ys, xs = np.mgrid[0:h, 0:w]
    cv.shade_px((ys % 3) == 2, 0.18)
    band = m_rect(w, h, 0, h // 2 - 3, w - 1, h // 2 + 2)
    cv.fill(band, RAMPS['ink'][3])
    for x in range(1, w - 3, 5):
        fm = m_rect(w, h, x, h // 2 - 2, x + 3, h // 2 + 1)
        cv.fill(fm, RAMPS['echo'][3])
        cv.px(x, h // 2 - 2, RAMPS['echo'][5])
    for x in range(1, w, 3):
        cv.px(x, h // 2 - 4, RAMPS['paper'][1])
        cv.px(x, h // 2 + 3, RAMPS['paper'][1])
    cv.shade_px(m_rect(w, h, 0, 0, w - 1, 0), 0.25, toward=(255, 250, 240))
    cv.shade_px(m_rect(w, h, 0, h - 1, w - 1, h - 1), 0.35)
    return cv


def reel_cap(w, h, rng):
    cv = mat('brass', w, h, rng, wear=0.3)
    cv.px(w // 2, h // 2, RAMPS['brass'][1])
    return cv


def film_tail(w, h, rng):
    cv = mat('paper', w, h, rng, bevel=0, wear=0.2, base_shift=-0.3)
    for x in range(1, w - 3, 5):
        cv.fill(m_rect(w, h, x, 1, x + 3, h - 2), RAMPS['echo'][3])
    return cv


def composition_reel():
    m = Model('composition_reel')
    wood_top = P(planks, 'planks_top')
    wood_side = P(lambda w, h, r: planks(w, h, r, 'side'), 'planks_side')
    m.box((1, 0, 1), (15, 2, 15), wood_side, up=wood_top, down=wood_top)
    for x0 in (1, 13):
        m.box((x0, 2, 3), (x0 + 2, 14, 13), F('copper', wear=0.6), east=P(reel_cheek, 'cheek'), west=P(reel_cheek, 'cheek'))
        m.box((x0, 14, 5), (x0 + 2, 15, 11), F('copper', shift=0.4), down=None)
        m.box((x0, 2, 1.5), (x0 + 2, 4, 14.5), F('dark_iron', wear=0.5), down=None)   # iron feet
    m.box((3, 4, 4), (13, 12, 12), P(reel_drum, 'drum'), east=F('paper', shift=-0.8), west=F('paper', shift=-0.8))
    m.box((0, 6.5, 6.5), (1, 9.5, 9.5), P(reel_cap, 'cap'))
    m.box((15, 6.5, 6.5), (16, 9.5, 9.5), P(reel_cap, 'cap'))
    m.box((4, 2, 3.2), (12, 5, 3.6), P(film_tail, 'tail'), rotation={'origin': [8, 2, 3.4], 'axis': 'x', 'angle': -22.5}, up=None, down=None)
    return m.build()


# ------------------------------------------------------------------ resonator trap

def cobble_plate_top(w, h, rng):
    cv = mat('mute', w, h, rng, face='top', bevel=1, wear=0.6, base_shift=0.5)
    for _ in range(9):
        x, y = rng.randrange(2, w - 5), rng.randrange(2, h - 5)
        raised(cv, x, y, x + rng.randint(2, 4), y + rng.randint(2, 3), 0.25)
    cx, cy = w / 2, h / 2
    for tx, ty in ((3, 3), (w - 4, 3), (3, h - 4), (w - 4, h - 4)):   # redstone traces to the posts
        cv.fill(m_line(w, h, (cx, cy), (tx, ty)), RAMPS['redstone'][3])
        cv.fill(m_line(w, h, (cx - 0.5, cy - 0.5), (tx - 0.5, ty - 0.5)) & ~m_line(w, h, (cx, cy), (tx, ty)), RAMPS['redstone'][1])
    cv.fill(m_ring(w, h, cx, cy, 6.5, 5.3), RAMPS['copper'][3])
    return cv


def post_side(w, h, rng):
    cv = mat('copper', w, h, rng, wear=0.8)
    for y in (2, h - 4):
        cv.fill(m_rect(w, h, 0, y, w - 1, y + 1), RAMPS['copper'][2])
        cv.fill(m_rect(w, h, 0, y, w - 1, y), RAMPS['copper'][5])
    return cv


def post_top(w, h, rng):
    cv = mat('verdigris', w, h, rng, face='top', wear=0.2)
    cv.px(w // 2 - 1, h // 2 - 1, RAMPS['verdigris'][6])
    return cv


def gel_core(w, h, rng):
    cv = mat('gel', w, h, rng, bevel=1, wear=0)
    cv.px(1, 1, RAMPS['gel'][6])
    return cv


def resonator_trap():
    m = Model('resonator_trap')
    m.box((1, 0, 1), (15, 2, 15), F('mute', shift=-0.3, wear=0.7), up=P(cobble_plate_top, 'plate_top'), down=P(cobble_plate_top, 'plate_top'))
    for x, z in ((1, 1), (12, 1), (1, 12), (12, 12)):
        m.box((x, 2, z), (x + 3, 12, z + 3), P(post_side, 'post'), up=P(post_top, 'post_top'), down=None)
    m.box((6, 2, 6), (10, 7, 10), P(gel_core, 'core'), down=None)
    m.box((5, 6, 5), (11, 7, 11), F('copper', shift=0.3), down=F('copper', shift=-1))
    m.box((6.5, 7, 6.5), (9.5, 8, 9.5), F('gel', wear=0, shift=1), down=None)
    for ang in (45, -45):   # copper tie bars between opposite posts
        m.box((1.5, 9, 7.5), (14.5, 10, 8.5), F('copper', wear=0.5, shift=-0.3), rotation={'origin': [8, 9.5, 8], 'axis': 'y', 'angle': ang})
    return m.build()


# ------------------------------------------------------------------ archive vault

def vault_pillar(w, h, rng):
    cv = mat('navy', w, h, rng, wear=0.5)
    for y in (0, h - 3):
        cv.fill(m_rect(w, h, 0, y, w - 1, y + 2), RAMPS['bone'][3])
        cv.fill(m_rect(w, h, 0, y, w - 1, y), RAMPS['bone'][5])
    return cv


def vault_slab_side(w, h, rng):
    cv = mat('navy', w, h, rng, wear=0.5, base_shift=0.2)
    cv.fill(m_rect(w, h, 0, h // 2, w - 1, h // 2), RAMPS['bone'][4])
    for x in range(3, w - 2, 8):
        rivet(cv, x, 0, 'brass')
    return cv


def vault_top(lit):
    def paint(w, h, rng):
        cv = mat('navy', w, h, rng, face='top', wear=0.4)
        well = m_rect(w, h, 4, 4, w - 5, h - 5)
        cv.fill(well, RAMPS['deep'][1] if not lit else RAMPS['amethyst'][1])
        ys, xs = np.mgrid[0:h, 0:w]
        if lit:
            d = np.sqrt((xs + 0.5 - w / 2) ** 2 + (ys + 0.5 - h / 2) ** 2)
            cv.shade_px(well & (d < w * 0.28), 0.5, toward=RAMPS['echo'][4])
        bars = well & (((xs - 4) % 6 < 2) | ((ys - 4) % 6 < 2))
        cv.fill(bars, RAMPS['amethyst'][3])
        cv.fill(bars & (((xs - 4) % 6 == 0) | ((ys - 4) % 6 == 0)), RAMPS['amethyst'][5])
        inset(cv, 3, 3, w - 4, h - 4, 0.3)
        for x, y in ((1, 1), (w - 3, 1), (1, h - 3), (w - 3, h - 3)):
            rivet(cv, x, y, 'brass')
        return cv
    return paint


def vault_back(w, h, rng):
    cv = mat('deep', w, h, rng, bevel=0, wear=0.2)
    return cv


def drawer_front(lit):
    def paint(w, h, rng):
        cv = mat('verdigris', w, h, rng, wear=0.6, base_shift=-0.4)
        core.speckle(cv, m_rect(w, h, 0, 0, w - 1, h - 1), rng, RAMPS['copper'][3], p=0.10)
        lx0, lx1 = w // 2 - 4, w // 2 + 3
        cv.fill(m_rect(w, h, lx0, 1, lx1, h - 2), RAMPS['brass'][2])
        cv.fill(m_rect(w, h, lx0 + 1, 2, lx1 - 1, h - 3), RAMPS['echo'][4] if lit else RAMPS['bone'][4])
        if lit:
            cv.px(lx0 + 2, 2, RAMPS['echo'][6]); cv.px(lx1 - 2, h - 3, RAMPS['echo'][6])
        else:
            cv.fill(m_rect(w, h, lx0 + 2, h // 2, lx1 - 2, h // 2), RAMPS['ink'][4])
        return cv
    return paint


def archive_vault(lit=False):
    name = 'archive_vault_on' if lit else 'archive_vault'
    m = Model(name, seed='archive_vault' + ('_on' if lit else ''))
    top = P(vault_top(lit), 'top')
    m.box((0, 0, 0), (16, 2, 16), P(vault_slab_side, 'slab'), up=None, down=F('navy', shift=-1, density=0.5))
    m.box((0, 14, 0), (16, 16, 16), P(vault_slab_side, 'slab'), up=top, down=None)
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        m.box((x, 2, z), (x + 2, 14, z + 2), P(vault_pillar, 'pillar'), up=None, down=None)
    m.box((1, 2, 1), (15, 14, 15), P(vault_back, 'back'), up=None, down=None)
    front = P(drawer_front(lit), 'drawer')
    edge = F('verdigris', shift=-1, wear=0.2)
    for y0, y1 in ((3, 6), (7, 10), (11, 13.5)):
        m.box((3, y0, 0.5), (13, y1, 1), edge, north=front, south=None)
        m.box((3, y0, 15), (13, y1, 15.5), edge, south=front, north=None)
        m.box((0.5, y0, 3), (1, y1, 13), edge, west=front, east=None)
        m.box((15, y0, 3), (15.5, y1, 13), edge, east=front, west=None)
        ym = (y0 + y1) / 2
        m.box((7.5, ym - 0.5, 0), (8.5, ym + 0.5, 0.5), F('brass', wear=0.2), south=None)
        m.box((7.5, ym - 0.5, 15.5), (8.5, ym + 0.5, 16), F('brass', wear=0.2), north=None)
        m.box((0, ym - 0.5, 7.5), (0.5, ym + 0.5, 8.5), F('brass', wear=0.2), east=None)
        m.box((15.5, ym - 0.5, 7.5), (16, ym + 0.5, 8.5), F('brass', wear=0.2), west=None)
    return m.build()


# ------------------------------------------------------------------ scar

def scar_glass_tex():
    rng = core.rng_for('scar_glass')
    w = h = 32
    cv = Canvas(w, h)
    n = core.fbm(w, h, rng, (8, 4), (0.6, 0.4), True)
    idx = 3.2 + (n - 0.5) * 2.2
    cv.paint(m_rect(w, h, 0, 0, w - 1, h - 1), 'scar', idx, alpha=150)
    a = cv.alpha.astype(int) + ((n - 0.5) * 60).astype(int)
    cv.alpha = np.clip(a, 110, 200).astype(np.uint8)
    for start in ((3, 0), (20, 4), (9, 17), (26, 20)):
        x, y = start
        for _ in range(12):
            if 0 <= x < w and 0 <= y < h:
                cv.rgb[y, x] = RAMPS['scar'][1]
                cv.alpha[y, x] = 230
                if y + 1 < h:
                    cv.rgb[(y + 1) % h, x] = RAMPS['magenta'][5]
                    cv.alpha[(y + 1) % h, x] = 220
            x = (x + rng.choice((-1, 0, 1, 1))) % w
            y = (y + rng.choice((0, 1, 1))) % h
    for x in range(w):   # frame seam that tiles into a lattice
        cv.rgb[0, x] = RAMPS['scar'][2]; cv.alpha[0, x] = 220
    for y in range(h):
        cv.rgb[y, 0] = RAMPS['scar'][2]; cv.alpha[y, 0] = 220
    for x, y in ((6, 5), (22, 12), (14, 26)):
        cv.rgb[y, x] = RAMPS['scar'][6]; cv.alpha[y, x] = 240
    return cv.image()


def scar_base_side(w, h, rng):
    cv = mat('deep', w, h, rng, wear=0.6)
    core.crack(cv, m_rect(w, h, 0, 0, w - 1, h - 1), rng, (w // 3, 0), h + 4, RAMPS['magenta'][4])
    return cv


def scar_base_top(w, h, rng):
    cv = mat('deep', w, h, rng, face='top', wear=0.6)
    cx, cy = w / 2, h / 2
    for ang in np.linspace(0, 2 * np.pi, 6, endpoint=False):
        cv.fill(m_line(w, h, (cx, cy), (cx + np.cos(ang) * w * 0.48, cy + np.sin(ang) * h * 0.48)), RAMPS['magenta'][3])
    cv.fill(m_ellipse(w, h, cx, cy, 4, 4), RAMPS['scar'][2])
    return cv


def scar_crystal(w, h, rng):
    cv = mat('scar', w, h, rng, wear=0.8, bevel=1)
    for y in range(2, h, 5):
        cv.fill(m_line(w, h, (0, y), (w - 1, y - 2)), RAMPS['magenta'][4])
    cv.fill(m_rect(w, h, 0, 0, 0, h - 1), RAMPS['scar'][6])
    return cv


def scar_heart():
    m = Model('scar_heart')
    m.box((2, 0, 2), (14, 3, 14), P(scar_base_side, 'base'), up=P(scar_base_top, 'base_top'), down=F('deep', shift=-1))
    crystal = P(scar_crystal, 'crystal')
    m.box((6, 3, 6), (10, 15, 10), crystal, down=None)
    m.box((3, 2, 7), (6, 11, 10), crystal, rotation={'origin': [4.5, 2, 8.5], 'axis': 'z', 'angle': 22.5})
    m.box((10, 2, 6), (13, 9, 9), crystal, rotation={'origin': [11.5, 2, 7.5], 'axis': 'z', 'angle': -22.5})
    m.box((7, 2, 10), (10, 10, 13), crystal, rotation={'origin': [8.5, 2, 11.5], 'axis': 'x', 'angle': 22.5})
    m.box((6, 2, 3), (9, 8, 6), crystal, rotation={'origin': [7.5, 2, 4.5], 'axis': 'x', 'angle': -22.5})
    return m.build()


# name -> builder returning (sheet image, model json); texture file = model name
MODELS = {
    'archival_stratum': archival_stratum,
    'mute_stone': mute_stone,
    'composition_reel': composition_reel,
    'resonator_trap': resonator_trap,
    'archive_vault': lambda: archive_vault(False),
    'archive_vault_on': lambda: archive_vault(True),
    'scar_heart': scar_heart,
}
CUBE_TEXTURES = {'scar_glass': scar_glass_tex}

# Break/sprint particles sample random corners of the particle sprite, so a packed sheet (with empty gaps) would
# give invisible or mismatched specks. Each model gets a small tiling sprite of its dominant material instead.
PARTICLE = {
    'archival_stratum': ('archival_stratum', 'navy'), 'mute_stone': ('mute_stone', 'mute'),
    'composition_reel': ('composition_reel', 'wood'), 'resonator_trap': ('resonator_trap', 'mute'),
    'archive_vault': ('archive_vault', 'deep'), 'archive_vault_on': ('archive_vault', 'deep'),
    'scar_heart': ('scar_heart', 'scar'),
}


def particle_sprite(name, mat):
    rng = core.rng_for(name + '_particle')
    return surface(mat, 32, 32, rng, face='side', bevel=0, wear=0.5, tile=True).image()
