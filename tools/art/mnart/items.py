"""Item sprites, 32x32. Light from the upper left, one sel-out outline, tools lie bottom-left to top-right."""
import numpy as np

from . import core, shapes
from .core import Canvas, m_rect, m_ellipse, m_poly, m_line, m_ring, dilate, erode
from .palette import RAMPS, mix, OUTLINE, c

S = 32


def new():
    return Canvas(S, S)


def finish(cv, selout=0.55):
    cv.outline(selout=selout)
    return cv.image()


# ------------------------------------------------------------------ instruments

def chronicle_lens():
    rng = core.rng_for('chronicle_lens')
    cv = new()
    cx, cy = 19.5, 12.5
    # handle first (behind the rim): leather grip, copper collar, brass end cap
    shapes.rod(cv, (5, 28), (13, 20), 4, 'leather', 3)
    for t in (0.25, 0.5, 0.75):   # wrap bands
        x, y = 5 + 8 * t, 28 - 8 * t
        cv.fill(shapes.rod_mask(S, S, (x - 0.6, y - 0.6), (x + 0.6, y + 0.6), 4.2) & dilate(cv.opaque()), RAMPS['leather'][1])
    shapes.rod(cv, (12, 21), (14.5, 18.5), 5, 'copper', 3)
    cv.part(m_ellipse(S, S, 4.5, 28.5, 2.4, 2.4), 'brass', 'sphere', 3)
    # rim: copper ring with an inner bone scale
    shapes.ring(cv, cx, cy, 10.5, 7.2, 'copper', 3)
    rimm = m_ring(S, S, cx, cy, 10.5, 7.2)
    core.speckle(cv, rimm & ~erode(rimm), rng, RAMPS['verdigris'][3], p=0.12)   # patina in the seams
    core.scratches(cv, rimm, rng, n=2, lighten=0.35)
    for k in range(12):   # engraved scale ticks
        a = k / 12 * 2 * np.pi
        x, y = int(cx + np.cos(a) * 8.8), int(cy + np.sin(a) * 8.8)
        cv.px(x, y, RAMPS['bone'][5] if k % 3 == 0 else RAMPS['copper'][1])
    # glass: amethyst-tinted, darker toward the lower right, two glints and a faint echo arc
    gm = m_ellipse(S, S, cx, cy, 7.2, 7.2)
    idx = core.shade(gm, 'grad', 4, direction=(1, 1), span=(1.2, -1.8), bevel=0)
    cv.paint(gm, 'amethyst', idx)
    inner = dilate(~gm) & gm
    cv.fill(inner & m_rect(S, S, 0, 0, S, int(cy)), RAMPS['amethyst'][2])
    cv.fill(m_line(S, S, (14, 12), (18, 8)) & gm, RAMPS['amethyst'][6])
    cv.fill(m_line(S, S, (15, 14), (20, 9)) & gm & erode(gm), RAMPS['amethyst'][5])
    cv.fill(m_line(S, S, (22, 17), (25, 14)) & gm, RAMPS['echo'][4])
    core.glint(cv, 16, 9, arm=1)
    # rim highlight on the lit shoulder
    cv.fill(m_ring(S, S, cx, cy, 10.5, 9.8) & m_rect(S, S, 0, 0, int(cx) - 2, int(cy) - 2), RAMPS['copper'][6])
    return finish(cv)


def extraction_needle():
    rng = core.rng_for('extraction_needle')
    cv = new()
    # wooden handle with a leather wrap, brass pommel
    shapes.rod(cv, (4, 28), (12, 20), 4, 'wood', 3, rng=rng, noise=0.3)
    for t in (0.35, 0.55, 0.75):
        x, y = 4 + 8 * t, 28 - 8 * t
        band = shapes.rod_mask(S, S, (x - 0.7, y + 0.7), (x + 0.7, y - 0.7), 4.6)
        idx = shapes.perp_index(S, S, (4, 28), (12, 20), 4, 3)
        cv.paint(band & dilate(cv.opaque()), 'leather', idx)
    cv.part(m_ellipse(S, S, 3.6, 28.4, 2.3, 2.3), 'brass', 'sphere', 3)
    # iron ferrule
    shapes.rod(cv, (11, 21), (14, 18), 5, 'iron', 3)
    cv.px(12, 18, RAMPS['iron'][6])
    # shaft: iron, tapering, a bright polished line
    shapes.rod(cv, (13, 19), (21, 11), 2.6, 'iron', 4)
    shapes.rod(cv, (20, 12), (25, 7), 1.8, 'iron', 4)
    core.scratches(cv, shapes.rod_mask(S, S, (13, 19), (21, 11), 2.6), rng, n=1)
    # amethyst tip crystal, glowing
    shapes.gem(cv, [(24, 7), (27, 2.5), (29.5, 3), (29, 5.5), (25.5, 8.5)], 'amethyst', 4)
    core.glint(cv, 27, 4, arm=0)
    shapes.glow(cv, 27, 4.5, 5, RAMPS['amethyst'][6], 0.35)
    return finish(cv)


# ------------------------------------------------------------------ slips

def _slip_base(name, ramp='paper', border=None, seal=None):
    rng = core.rng_for(name)
    cv = new()
    m = shapes.slip(cv, 7, 3, 25, 29, rng, ramp)
    if border:
        rim = m & ~erode(erode(m))
        cv.fill(rim & ~m_poly(S, S, [(21, 3), (25, 7), (25, 3)]), RAMPS[border][2])
        cv.fill(rim & ~erode(m) & m_rect(S, S, 0, 0, 16, 32), RAMPS[border][3])
    core.grime(cv, m, rng, amount=0.12, t=0.14)
    if seal:
        sm = m_ellipse(S, S, 11.5, 25.5, 3.2, 3.2)
        cv.part(sm, seal, 'sphere', 3)
        cv.px(10, 24, RAMPS[seal][6])
    return cv, m, rng


def imprint_slip():
    cv, m, rng = _slip_base('imprint_slip', seal='indigo')
    # a pressed memory wave: indigo ink, verdigris where it still glows
    pts = [(9, 14), (11, 11), (13, 16), (15, 9), (17, 17), (19, 12), (21, 14), (23, 13)]
    cv.fill(core.m_lines(S, S, pts, 1) & m, RAMPS['indigo'][2])
    for x, y in pts[2:6]:
        cv.px(x, y, RAMPS['verdigris'][4])
    for y in (20, 22):
        cv.fill(m_rect(S, S, 15, y, 22, y) & m, RAMPS['paper'][2])
    return finish(cv)


def _echo_figure(cv, m, x, y, ramp='echo', s=1.0, alpha_rows=False):
    fm = shapes.figure_mask(S, S, x, y, s) & m
    idx = core.shade(fm, 'grad', 4, direction=(0.3, 1), span=(1.2, -1.2))
    cv.paint(fm, ramp, idx)
    return fm


def echo_slip():
    cv, m, rng = _slip_base('echo_slip', seal='redstone')
    fm = _echo_figure(cv, m, 11, 6, s=0.95)
    # the recording trail: two fading after-images to the right
    for k, dx in enumerate((4, 7)):
        tm = shapes.figure_mask(S, S, 11 + dx, 6, 0.95) & m & ~dilate(fm)
        cv.fill(tm & (np.indices((S, S)).sum(0) % (2 + k) == 0), RAMPS['echo'][3 + k])
    return finish(cv)


def echo_recording():
    rng = core.rng_for('echo_recording')
    cv = new()
    # a wound film strip: copper spool cheeks, a paper band with pink frames, a loose tail
    tail = m_poly(S, S, [(14, 20), (27, 25), (26, 29), (13, 24)])
    cv.part(tail, 'paper', 'grad', 4, direction=(0, 1), span=(0.6, -0.8))
    for x in (17, 21, 25):
        cv.fill(m_rect(S, S, x, 22 + (x - 17) // 3, x + 1, 23 + (x - 17) // 3) & tail, RAMPS['echo'][4])
    cv.part(m_ellipse(S, S, 13, 14, 10.5, 10.5), 'copper', 'sphere', 3)
    body = m_ellipse(S, S, 13, 14, 8.5, 8.5)
    cv.part(body, 'paper', 'sphere', 4)
    for r in (7.4, 5.6):
        cv.fill(m_ring(S, S, 13, 14, r + 0.5, r - 0.4), RAMPS['paper'][2])
    for k in range(8):
        a = k / 8 * 2 * np.pi + 0.3
        cv.px(int(13 + np.cos(a) * 6.5), int(14 + np.sin(a) * 6.5), RAMPS['echo'][4])
    hub = m_ellipse(S, S, 13, 14, 3.2, 3.2)
    cv.part(hub, 'brass', 'sphere', 3)
    cv.px(12, 13, RAMPS['brass'][6])
    cv.fill(m_ellipse(S, S, 13, 14, 1.1, 1.1), OUTLINE)
    core.scratches(cv, m_ring(S, S, 13, 14, 10.5, 8.5), rng, n=2)
    return finish(cv)


def _upgrade(name, border, emblem):
    cv, m, rng = _slip_base(name, border=border, seal='redstone')
    emblem(cv, m, rng)
    return finish(cv)


def echo_chorus_slip():
    def em(cv, m, rng):
        for dx, ramp, shade in ((6, 'echo', -1), (0, 'echo', 0), (3, 'echo', 1)):
            fm = shapes.figure_mask(S, S, 9 + dx, 6 + (2 if shade else 0), 0.85) & m
            cv.paint(fm, ramp, core.shade(fm, 'grad', 4 + shade, direction=(0.3, 1), span=(1, -1.2)))
        cv.fill(m_rect(S, S, 17, 25, 22, 25) & m, RAMPS['amethyst'][3])
    return _upgrade('echo_chorus_slip', 'amethyst', em)


def echo_long_slip():
    def em(cv, m, rng):
        # a stretched take: one figure and a long gold time ribbon with frame ticks
        fm = shapes.figure_mask(S, S, 9, 6, 0.85) & m
        cv.paint(fm, 'echo', core.shade(fm, 'grad', 4, direction=(0.3, 1), span=(1, -1.2)))
        band = m_rect(S, S, 12, 15, 23, 17) & m
        cv.part(band, 'gold', 'cyl_h', 4)
        for x in range(13, 23, 3):
            cv.px(x, 16, RAMPS['gold'][1])
        cv.fill(m_poly(S, S, [(22, 13), (24, 16), (22, 19)]) & m, RAMPS['gold'][3])
        cv.fill(m_ring(S, S, 19, 23, 3.2, 2.1) & m, RAMPS['brass'][2])   # a clock face
        cv.px(19, 22, RAMPS['brass'][1]); cv.px(20, 23, RAMPS['brass'][1])
    return _upgrade('echo_long_slip', 'gold', em)


def echo_sturdy_slip():
    def em(cv, m, rng):
        sh = m_poly(S, S, [(11, 7), (21, 7), (21, 15), (16, 21), (11, 15)]) & m
        cv.part(sh, 'navy', 'grad', 3, direction=(1, 1), span=(1.4, -1.2))
        cv.fill(m_rect(S, S, 11, 10, 21, 11) & sh, RAMPS['bone'][4])        # stratum band
        fm = shapes.figure_mask(S, S, 13, 9, 0.55) & sh
        cv.paint(fm, 'echo', core.shade(fm, 'flat', 4))
        cv.fill(m_rect(S, S, 14, 23, 22, 24) & m, RAMPS['iron'][4])         # iron plate
        cv.px(14, 23, RAMPS['iron'][6])
    return _upgrade('echo_sturdy_slip', 'iron', em)


def unstable_slip():
    cv, m, rng = _slip_base('unstable_slip', seal=None)
    # scorched, torn through, ember light leaking out of the tear
    tear = core.m_lines(S, S, [(14, 3), (17, 9), (13, 14), (18, 19), (15, 24), (17, 29)], 1)
    glowm = dilate(tear) & m
    cv.fill(glowm, RAMPS['ember'][3])
    cv.fill(tear & m, RAMPS['ember'][6])
    cv.alpha[(core.m_lines(S, S, [(14, 3), (17, 9), (15, 12)], 1)) & m] = 0
    core.grime(cv, m & m_rect(S, S, 7, 18, 25, 29), rng, amount=0.4, t=0.35)
    cv.fill(m_rect(S, S, 9, 8, 11, 8) & m, RAMPS['paper'][2])
    return finish(cv)


# ------------------------------------------------------------------ books and paper

def catalog_fragment():
    rng = core.rng_for('catalog_fragment')
    cv = new()
    page = m_poly(S, S, [(8, 4), (26, 4), (26, 11), (23, 13), (25, 16), (22, 19), (24, 23), (21, 28), (8, 28)])
    cv.part(page, 'paper', 'grad', 4, rng=rng, noise=0.2, direction=(1, 0.4), span=(0.6, -0.9))
    spine = m_rect(S, S, 6, 4, 9, 28)
    cv.part(spine, 'indigo', 'cyl_v', 3)
    for y in (7, 16, 25):
        cv.fill(m_rect(S, S, 6, y, 9, y), RAMPS['brass'][4])
    for i, y in enumerate(range(8, 26, 3)):   # index rows: tag dot + ruled line
        cv.px(11, y, [RAMPS['ember'][3], RAMPS['verdigris'][3], RAMPS['amethyst'][4]][i % 3])
        cv.fill(m_rect(S, S, 13, y, 13 + (7 if i % 2 else 5), y) & page, RAMPS['ink'][4])
    rib = m_poly(S, S, [(17, 2), (19, 2), (19, 9), (18, 8), (17, 9)])
    cv.part(rib, 'verdigris', 'flat', 3)
    core.grime(cv, page, rng, amount=0.15, t=0.12)
    return finish(cv)


def field_guide():
    rng = core.rng_for('field_guide')
    cv = new()
    pages = m_poly(S, S, [(9, 5), (27, 5), (27, 27), (9, 27)])
    cv.part(pages, 'bone', 'flat', 4)
    for y in range(7, 27, 2):
        cv.fill(m_rect(S, S, 25, y, 27, y), RAMPS['bone'][2])
    cover = m_poly(S, S, [(5, 4), (24, 4), (24, 28), (5, 28)])
    cv.part(cover, 'indigo', 'grad', 3, rng=rng, noise=0.25, direction=(1, 1), span=(1.0, -1.2))
    spine = m_rect(S, S, 5, 4, 8, 28)
    cv.part(spine, 'ink', 'cyl_v', 4)
    for y in (7, 24):
        cv.fill(m_rect(S, S, 5, y, 8, y), RAMPS['brass'][4])
    for (x, y) in ((22, 4), (22, 26)):   # brass corner guards
        cv.part(m_poly(S, S, [(x, y), (x + 2, y), (x + 2, y + 2)] if y == 4 else [(x + 2, y), (x + 2, y + 2), (x, y + 2)]), 'brass', 'flat', 4)
    # embossed lens on the cover, amethyst centre
    cv.fill(m_ring(S, S, 15.5, 14.5, 5.2, 3.8), RAMPS['brass'][3])
    cv.fill(m_ring(S, S, 15.5, 14.5, 5.2, 4.6) & m_rect(S, S, 0, 0, 15, 14), RAMPS['brass'][5])
    cv.part(m_ellipse(S, S, 15.5, 14.5, 3.8, 3.8), 'amethyst', 'sphere', 4)
    cv.fill(m_rect(S, S, 11, 22, 20, 22), RAMPS['indigo'][1])
    core.chips(cv, cover & m_rect(S, S, 5, 4, 24, 5), rng, 2)
    core.grime(cv, cover, rng, amount=0.08)
    # clasp
    cv.part(m_rect(S, S, 23, 13, 27, 17), 'brass', 'flat', 3)
    cv.px(24, 14, RAMPS['brass'][6])
    return finish(cv)


def archival_tablet():
    rng = core.rng_for('archival_tablet')
    cv = new()
    tab = m_poly(S, S, [(7, 5), (24, 4), (26, 6), (26, 27), (9, 28), (6, 25)])
    cv.part(tab, 'navy', 'grad', 3, rng=rng, noise=0.3, direction=(1, 1), span=(1.2, -1.2))
    face = erode(erode(tab))
    cv.fill(face & ~erode(face), RAMPS['navy'][2])
    for i, y in enumerate(range(9, 25, 3)):
        x1 = 22 if i % 2 == 0 else 19
        cv.fill(m_rect(S, S, 10, y, x1, y) & face, RAMPS['bone'][4])
        cv.fill(m_rect(S, S, 10, y + 1, x1, y + 1) & face, RAMPS['navy'][1])
    gm = m_poly(S, S, [(20, 20), (24, 20), (24, 24), (20, 24)]) & face
    cv.part(gm, 'verdigris', 'flat', 4)
    core.glint(cv, 21, 21, arm=0)
    cv.alpha[m_poly(S, S, [(24, 4), (26, 4), (26, 7)])] = 0   # chipped corner
    core.crack(cv, face, rng, (12, 26), 4, RAMPS['navy'][0])
    return finish(cv)


# ------------------------------------------------------------------ archivist drops

def archivist_bait():
    rng = core.rng_for('archivist_bait')
    cv = new()
    # a lure: waxed string, a bone hook, a verdigris bead and a paper scrap the archivist cannot resist
    cv.fill(core.m_lines(S, S, [(16, 2), (16, 9)], 1), RAMPS['bone'][2])
    tag = m_poly(S, S, [(18, 4), (26, 3), (27, 9), (19, 10)])
    cv.part(tag, 'paper', 'flat', 4)
    cv.fill(m_rect(S, S, 20, 6, 24, 6) & tag, RAMPS['ink'][4])
    cv.fill(core.m_lines(S, S, [(16, 5), (19, 6)], 1), RAMPS['bone'][2])
    bead = m_ellipse(S, S, 16.5, 12.5, 3.3, 3.3)
    cv.part(bead, 'verdigris', 'sphere', 3)
    core.glint(cv, 15, 11, arm=0)
    hook = core.m_lines(S, S, [(16, 15), (16, 23), (14, 27), (10, 27), (8, 24), (8, 21)], 3)
    cv.part(hook, 'bone', 'flat', 4)
    barb = m_poly(S, S, [(6, 21), (8, 18), (10, 21)])
    cv.part(barb, 'bone', 'flat', 5)
    cv.fill(core.m_lines(S, S, [(15, 16), (15, 22)], 1), RAMPS['bone'][6])
    return finish(cv)


def archivist_husk():
    rng = core.rng_for('archivist_husk')
    cv = new()
    hood = m_poly(S, S, [(16, 2), (24, 8), (27, 18), (26, 27), (21, 24), (17, 28), (13, 24), (8, 28), (5, 20), (7, 9)])
    cv.part(hood, 'indigo', 'sphere', 3, rng=rng, noise=0.3)
    void = m_ellipse(S, S, 16, 15, 5.2, 6.4)
    cv.part(void, 'ink', 'grad', 1, direction=(0, 1), span=(-0.5, 0.5), bevel=0)
    cv.fill(dilate(void) & ~void & m_rect(S, S, 0, 0, 32, 14), RAMPS['indigo'][5])
    # folds and a torn hem
    for p0, p1 in (((9, 12), (8, 22)), ((23, 12), (24, 22))):
        cv.fill(m_line(S, S, p0, p1) & hood & ~void, RAMPS['indigo'][1])
    cv.fill(m_line(S, S, (10, 12), (9, 20)) & hood & ~void, RAMPS['indigo'][5])
    core.stitch(cv, [(x, 26 - (x % 3)) for x in range(9, 25)], RAMPS['indigo'][1])
    cv.fill(m_rect(S, S, 15, 3, 17, 4), RAMPS['bone'][4])   # the bone clasp
    return finish(cv)


# ------------------------------------------------------------------ memory materials

def residual_shard():
    rng = core.rng_for('residual_shard')
    cv = new()
    pts = [(8, 27), (6, 22), (15, 10), (22, 3), (26, 5), (24, 13), (15, 25)]
    m = shapes.gem(cv, pts, 'pale', 4)
    core_m = shapes.rod_mask(S, S, (10, 23), (22, 8), 3) & erode(m)
    cv.part(core_m, 'echo', 'flat', 4, bevel=0)
    cv.fill(shapes.rod_mask(S, S, (11, 22), (21, 9), 1) & core_m, RAMPS['echo'][6])
    shapes.glow(cv, 16, 16, 9, RAMPS['echo'][5], 0.25)
    core.glint(cv, 23, 6, arm=1)
    cv.px(9, 21, RAMPS['verdigris'][5])
    for _ in range(3):
        x, y = rng.randrange(4, 28), rng.randrange(4, 28)
        if not m[y, x]:
            cv.px(x, y, RAMPS['pale'][5], 200)
    return finish(cv)


def scar_fragment():
    rng = core.rng_for('scar_fragment')
    cv = new()
    main = shapes.gem(cv, [(6, 26), (9, 17), (18, 6), (25, 3), (23, 11), (15, 22), (10, 28)], 'scar', 4)
    side = shapes.gem(cv, [(16, 24), (20, 16), (26, 14), (24, 21), (18, 27)], 'scar', 3)
    seam = core.m_lines(S, S, [(9, 24), (14, 17), (19, 11), (23, 6)], 1) & main
    cv.fill(seam, RAMPS['magenta'][5])
    cv.fill(core.m_lines(S, S, [(19, 23), (23, 17)], 1) & side, RAMPS['magenta'][4])
    core.glint(cv, 21, 7, arm=1)
    for (x, y), col in zip(((5, 12), (27, 24), (12, 5), (28, 9)), ('fire', 'fall', 'death', 'explosion')):
        from .palette import TEMPER
        cv.px(x, y, TEMPER[col])
    return finish(cv)


def relay_thread():
    rng = core.rng_for('relay_thread')
    cv = new()
    # a bone spool wound with copper thread; the free ends end in two pink echo knots
    sp_t = m_ellipse(S, S, 16, 8, 8, 3)
    sp_b = m_ellipse(S, S, 16, 24, 8, 3)
    cv.part(sp_b, 'bone', 'flat', 3)
    body = m_rect(S, S, 9, 8, 23, 24)
    idx = core.shade(body, 'cyl_v', 3)
    rows = np.indices((S, S))[0]
    idx = idx + np.where(rows % 2 == 0, 0.5, -0.5)
    cv.paint(body, 'copper', idx)
    core.speckle(cv, body, rng, RAMPS['verdigris'][3], p=0.03)
    cv.part(sp_t, 'bone', 'sphere', 4)
    cv.part(m_ellipse(S, S, 16, 8, 2.2, 1.0), 'wood', 'flat', 1, bevel=0)
    cv.fill(core.m_lines(S, S, [(23, 14), (27, 17), (28, 22), (26, 27)], 1), RAMPS['copper'][4])
    cv.fill(core.m_lines(S, S, [(9, 18), (5, 22), (4, 26)], 1), RAMPS['copper'][4])
    for x, y in ((26, 27), (4, 27)):
        cv.part(m_ellipse(S, S, x + 0.5, y + 0.5, 2.1, 2.1), 'echo', 'sphere', 4)
    return finish(cv)


# ------------------------------------------------------------------ spawn eggs

def _egg(name, shell, spots, spot_i=4, band=None):
    rng = core.rng_for(name)
    cv = new()
    m = m_ellipse(S, S, 16, 17, 9.5, 12.5) & ~m_rect(S, S, 0, 0, 32, 4)
    cv.part(m, shell, 'sphere', 3, rng=rng, noise=0.2)
    for _ in range(9):
        x, y = rng.randrange(9, 23), rng.randrange(8, 27)
        r = rng.choice((1.2, 1.6, 2.0))
        sm = m_ellipse(S, S, x + 0.5, y + 0.5, r, r) & erode(m)
        cv.paint(sm, spots, core.shade(sm, 'flat', spot_i, bevel=0.5))
    if band:
        bm = m_rect(S, S, 0, 16, 32, 18) & m
        cv.paint(bm, band, core.shade(bm, 'cyl_h', 3))
    cv.fill(m_ellipse(S, S, 12.5, 11, 2.2, 3.4) & m, mix(RAMPS[shell][5], (255, 255, 255), 0.3))
    return finish(cv)


def echo_strider_spawn_egg():
    return _egg('echo_strider_spawn_egg', 'verdigris', 'bone', 4)


def archivist_spawn_egg():
    return _egg('archivist_spawn_egg', 'indigo', 'bone', 4, band='brass')


def moment_replicant_spawn_egg():
    return _egg('moment_replicant_spawn_egg', 'bone', 'indigo', 3, band='ember')


ITEMS = {
    'chronicle_lens': chronicle_lens,
    'extraction_needle': extraction_needle,
    'imprint_slip': imprint_slip,
    'echo_slip': echo_slip,
    'echo_recording': echo_recording,
    'echo_chorus_slip': echo_chorus_slip,
    'echo_long_slip': echo_long_slip,
    'echo_sturdy_slip': echo_sturdy_slip,
    'unstable_slip': unstable_slip,
    'catalog_fragment': catalog_fragment,
    'field_guide': field_guide,
    'archival_tablet': archival_tablet,
    'archivist_bait': archivist_bait,
    'archivist_husk': archivist_husk,
    'residual_shard': residual_shard,
    'scar_fragment': scar_fragment,
    'relay_thread': relay_thread,
    'echo_strider_spawn_egg': echo_strider_spawn_egg,
    'archivist_spawn_egg': archivist_spawn_egg,
    'moment_replicant_spawn_egg': moment_replicant_spawn_egg,
}
