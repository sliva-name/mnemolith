"""Armory mobs, worn armor, and item sprites in the same material language as the rest of the mod.

Entity and armor sheets are painted at two texels per model unit. Item icons are 32×32.
Weapons also get an element model for the hand; the GUI keeps the flat sprite.
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(REPO, 'src', 'main', 'resources', 'assets', 'mnemolith')

from mnart import core, shapes  # noqa: E402
from mnart.core import Canvas, m_ellipse, m_line, m_rect, m_poly  # noqa: E402
from mnart.entities import Sheet, regions  # noqa: E402
from mnart.handheld import HANDHELD, crystal, grip, shaft  # noqa: E402
from mnart.items import _egg, finish, new  # noqa: E402
from mnart.materials import surface  # noqa: E402
from mnart.modelgen import F, Model, P, dump  # noqa: E402
from mnart.palette import OUTLINE, RAMPS, mix  # noqa: E402

S = 32


def save(rel, img):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=False)


def face_of(mat, **kw):
    def paint(f, w, h, rng):
        kind = 'top' if f == 'up' else 'bottom' if f == 'down' else 'side'
        return surface(mat, w, h, rng, face=kind, **kw)
    return paint


def seam(cv, y0, y1, color):
    h, w = cv.alpha.shape
    cv.fill(m_rect(w, h, 0, y0, w - 1, y1), color)


def pits(cv, ys, color):
    """Two shallow grooves. Not eyes."""
    h, w = cv.alpha.shape
    for x in (max(1, w // 5), max(1, w - w // 5 - 2)):
        cv.fill(m_rect(w, h, x, ys, x + 1, ys + 2), color)


# ---------------------------------------------------------------- mobs

def ledger_mite():
    s = Sheet('ledger_mite')

    def abdomen(f, w, h, rng):
        cv = surface('bone', w, h, rng, face='top' if f == 'up' else 'side', wear=0.55, base_shift=-0.2)
        if f in ('north', 'south', 'left', 'right'):
            for x in range(2, w, 4):
                cv.fill(m_rect(w, h, x, 0, x, h - 1), RAMPS['ink'][2])
            seam(cv, h - 2, h - 1, RAMPS['indigo'][2])
        if f == 'up':
            cv.fill(m_rect(w, h, 1, h // 2, w - 2, h // 2), RAMPS['indigo'][3])
        return cv

    def thorax(f, w, h, rng):
        cv = surface('bone', w, h, rng, face='side', wear=0.4, base_shift=0.4)
        if f == 'north':
            seam(cv, h // 2, h // 2, RAMPS['verdigris'][4])
        return cv

    def head(f, w, h, rng):
        cv = surface('bone', w, h, rng, wear=0.35, base_shift=0.6)
        if f == 'north':
            pits(cv, h // 2, RAMPS['ink'][0])
            seam(cv, h - 2, h - 1, RAMPS['ink'][3])
        return cv

    def mandible(f, w, h, rng):
        cv = surface('iron', w, h, rng, wear=0.6, base_shift=-0.3)
        if f in ('north', 'left', 'right'):
            cv.fill(m_rect(w, h, 0, 0, w - 1, 0), RAMPS['iron'][6])
        return cv

    def antenna(f, w, h, rng):
        cv = surface('ink', w, h, rng, wear=0.2)
        if f != 'down':
            cv.fill(m_rect(w, h, 0, 0, w - 1, max(0, h // 5)), RAMPS['bone'][5])
        return cv

    def ledger(f, w, h, rng):
        cv = surface('paper', w, h, rng, wear=0.7, base_shift=0.2)
        if f == 'up':
            for x in range(2, w, 3):
                cv.px(x, h // 2, RAMPS['indigo'][2])
            cv.fill(m_ellipse(w, h, w // 2, h // 2, 2.2, 2.2), RAMPS['redstone'][4])
            cv.px(w // 2, h // 2, RAMPS['redstone'][6])
        if f in ('north', 'south'):
            cv.fill(m_rect(w, h, 0, 0, w - 1, 0), RAMPS['leather'][2])
        return cv

    def leg(f, w, h, rng):
        cv = surface('bone', w, h, rng, wear=0.5, base_shift=-0.4)
        if f in ('north', 'south', 'left', 'right') and h > 2:
            cv.fill(m_rect(w, h, 0, h // 2, w - 1, h // 2), RAMPS['ink'][2])
        return cv

    s.box(0, 0, 8, 3, 7, abdomen)
    s.box(0, 12, 6, 3, 5, thorax)
    s.box(32, 0, 4, 3, 4, head)
    s.box(32, 8, 2, 1, 3, mandible)
    s.box(44, 8, 2, 1, 3, mandible)
    s.box(48, 0, 1, 6, 1, antenna)
    s.box(54, 0, 1, 6, 1, antenna)
    s.box(16, 24, 5, 1, 4, ledger)
    s.box(0, 24, 1, 3, 1, leg)
    s.box(8, 24, 1, 2, 1, leg)
    return s.image()


def kin_witness():
    s = Sheet('kin_witness')

    def robe(f, w, h, rng):
        cv = surface('indigo', w, h, rng, face='side', wear=0.45)
        if f in ('north', 'south', 'left', 'right'):
            for x in range(1, w, 3):
                cv.shade_px(m_rect(w, h, x, 0, x, h - 1), 0.28)
            seam(cv, h - 5, h - 4, RAMPS['bone'][4])
            cv.fill(m_rect(w, h, w // 2 - 1, h - 6, w // 2, h - 3), RAMPS['brass'][4])
            if f == 'north':
                cv.fill(m_line(w, h, (1, 2), (w - 2, h // 2)), RAMPS['bone'][3])
        return cv

    def mask(f, w, h, rng):
        cv = surface('bone', w, h, rng, wear=0.35, base_shift=0.5)
        if f == 'north':
            cv.fill(m_rect(w, h, 2, 2, w - 3, h - 2), RAMPS['ink'][0])
            cv.fill(m_rect(w, h, 3, 3, w - 4, 3), RAMPS['bone'][6])
            cv.fill(m_rect(w, h, w // 2, 4, w // 2, h - 3), RAMPS['amethyst'][5])
        if f == 'up':
            cv.fill(m_line(w, h, (w // 2, 0), (w // 2, h - 1)), RAMPS['bone'][2])
        return cv

    def ring_face(mat, hole=0.55):
        def paint(f, w, h, rng):
            kind = 'top' if f in ('up', 'down') else 'side'
            cv = surface(mat, w, h, rng, face=kind, wear=0.3, base_shift=0.4)
            if f in ('up', 'down'):
                cx, cy = (w - 1) / 2, (h - 1) / 2
                rad = min(w, h) * hole / 2
                ys, xs = __import__('numpy').mgrid[0:h, 0:w]
                cv.alpha[((xs - cx) ** 2 + (ys - cy) ** 2) < rad * rad] = 0
            return cv
        return paint

    def arm(f, w, h, rng):
        cv = surface('indigo', w, h, rng, wear=0.4, base_shift=-0.3)
        if f in ('north', 'south', 'left', 'right'):
            seam(cv, h - 4, h - 1, RAMPS['bone'][3])
        return cv

    def tablet(f, w, h, rng):
        cv = surface('paper', w, h, rng, wear=0.5)
        if f == 'north':
            for y in (3, 6, 9, 12):
                if y < h:
                    cv.fill(m_rect(w, h, 2, y, w - 3, y), RAMPS['indigo'][2])
            cv.fill(m_rect(w, h, 2, 2, 4, 4), RAMPS['echo'][5])
        return cv

    def strip(f, w, h, rng):
        cv = surface('paper', w, h, rng, wear=0.8, base_shift=-0.2)
        if f in ('north', 'south'):
            for x in range(w):
                cut = rng.choice((0, 0, 1, 2, 3))
                for y in range(h - cut, h):
                    cv.alpha[y, x] = 0
            cv.fill(m_rect(w, h, 0, 0, w - 1, 0), RAMPS['indigo'][3])
        return cv

    def leg(f, w, h, rng):
        cv = surface('ink', w, h, rng, wear=0.35)
        if f in ('north', 'south', 'left', 'right'):
            seam(cv, 2, 2, RAMPS['brass'][3])
            seam(cv, h - 3, h - 1, RAMPS['bone'][3])
        return cv

    def foot(f, w, h, rng):
        return surface('bone', w, h, rng, face='top' if f == 'up' else 'side', wear=0.4)

    s.box(0, 0, 2, 12, 2, leg)
    s.box(16, 0, 3, 2, 4, foot)
    s.box(0, 16, 5, 14, 4, robe)
    s.box(20, 16, 7, 7, 3, mask)
    s.box(0, 48, 8, 1, 8, ring_face('amethyst', 0.62))
    s.box(0, 36, 10, 1, 10, ring_face('brass', 0.58))
    s.box(42, 0, 2, 11, 2, arm)
    s.box(42, 16, 6, 8, 1, tablet)
    s.box(42, 28, 1, 12, 3, strip)
    s.box(52, 28, 1, 12, 3, strip)
    return s.image()


def _stalker(name, elite):
    s = Sheet(name)
    hide = 'scar' if elite else 'deep'
    horn = 'brass' if elite else 'bone'
    slit = RAMPS['ember'][6] if elite else RAMPS['scar'][5]

    def body(f, w, h, rng):
        cv = surface(hide, w, h, rng, face='top' if f == 'up' else 'side', wear=0.7 if elite else 0.45, base_shift=-0.2)
        if f == 'up':
            for x in range(3, w, 6):
                cv.fill(m_rect(w, h, x, 1, x + 1, h - 2), RAMPS['bone'][3 if not elite else 5])
        if f in ('north', 'south', 'left', 'right'):
            cv.fill(m_line(w, h, (1, 1), (w - 2, h - 2)), RAMPS['ember'][4] if elite else RAMPS['mute'][1])
            if elite and f == 'north':
                cv.fill(m_rect(w, h, w // 2, 1, w // 2, h - 2), RAMPS['ember'][5])
        return cv

    def head(f, w, h, rng):
        cv = surface(hide, w, h, rng, wear=0.5, base_shift=0.2)
        if f == 'north':
            cv.fill(m_rect(w, h, 2, h // 2, w - 3, h // 2), slit)
            cv.fill(m_rect(w, h, 2, h // 2 + 1, w - 3, h // 2 + 1), RAMPS['ink'][0])
        return cv

    def snout(f, w, h, rng):
        cv = surface('mute', w, h, rng, wear=0.6, base_shift=-0.4)
        if f == 'north':
            for x in (1, w - 2):
                cv.px(x, h // 2, RAMPS['bone'][5])
        return cv

    def jaw(f, w, h, rng):
        cv = surface('ink', w, h, rng, wear=0.4)
        if f == 'up':
            for x in range(1, w, 2):
                cv.px(x, h // 2, RAMPS['bone'][6])
        return cv

    def horn_paint(f, w, h, rng):
        cv = surface(horn, w, h, rng, wear=0.25, base_shift=0.5)
        if f in ('north', 'south', 'left', 'right'):
            cv.fill(m_rect(w, h, 0, 0, w - 1, 1), RAMPS[horn][6])
        return cv

    def spine(f, w, h, rng):
        return surface('bone', w, h, rng, wear=0.3, base_shift=0.8)

    def tail(f, w, h, rng):
        return surface(hide, w, h, rng, wear=0.5)

    def tip(f, w, h, rng):
        cv = surface('scar' if elite else 'mute', w, h, rng, wear=0.2, base_shift=0.6)
        if f == 'up':
            cv.fill(m_rect(w, h, 1, 0, w - 2, h - 1), RAMPS['ember'][5] if elite else RAMPS['scar'][4])
        return cv

    def leg(f, w, h, rng):
        cv = surface(hide, w, h, rng, wear=0.55)
        if f in ('north', 'south', 'left', 'right'):
            seam(cv, h - 3, h - 1, RAMPS['ink'][1])
        return cv

    def paw(f, w, h, rng):
        cv = surface('ink', w, h, rng, wear=0.4)
        if f == 'down':
            for x in (1, w // 2, w - 2):
                cv.px(min(w - 1, x), h // 2, RAMPS['bone'][4])
        return cv

    def shoulder(f, w, h, rng):
        cv = surface('bone' if not elite else 'brass', w, h, rng, wear=0.45)
        if f == 'up':
            cv.fill(m_rect(w, h, 0, 0, w - 1, 0), RAMPS['iron'][5])
        return cv

    s.box(0, 0, 8, 5, 14, body)
    s.box(0, 20, 2, 2, 8, tail)
    s.box(22, 20, 3, 1, 4, tip)
    s.box(0, 32, 6, 5, 6, head)
    s.box(26, 32, 4, 3, 5, snout)
    s.box(26, 42, 4, 2, 4, jaw)
    s.box(46, 0, 2, 5, 2, horn_paint)
    s.box(46, 8, 2, 5, 2, horn_paint)
    s.box(56, 0, 1, 4, 2, spine)
    s.box(46, 20, 3, 7, 3, leg)
    s.box(46, 32, 4, 2, 4, paw)
    s.box(0, 46, 5, 2, 3, shoulder)
    return s.image()


# ---------------------------------------------------------------- armor (player UV, 64×32, stored at 128×64)

class ArmorSheet:
    def __init__(self, name):
        self.img = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
        self.rng = core.rng_for(name)

    def box(self, u, v, w, h, d, painter):
        for f, (x, y, fw, fh) in regions(u, v, w, h, d).items():
            cv = painter(f, fw * 2, fh * 2, self.rng)
            if cv is not None:
                self.img.alpha_composite(cv.image(), (x * 2, y * 2))

    def image(self):
        return self.img


def _plate(mat, trim, visor=False, lattice=False, boot=False):
    def paint(f, w, h, rng):
        kind = 'top' if f == 'up' else 'bottom' if f == 'down' else 'side'
        cv = surface(mat, w, h, rng, face=kind, wear=0.45)
        if f in ('north', 'south', 'left', 'right'):
            cv.fill(m_rect(w, h, 0, 0, w - 1, 0), RAMPS[trim][5])
            cv.fill(m_rect(w, h, 0, h - 1, w - 1, h - 1), RAMPS[trim][1])
            if visor and f == 'north' and h > 6:
                cv.alpha[h // 3: h // 3 + max(2, h // 6), 2:w - 2] = 0
                cv.fill(m_rect(w, h, 2, h // 3 - 1, w - 3, h // 3 - 1), RAMPS[trim][6])
            if lattice and f in ('north', 'south'):
                for x in range(2, w - 1, 3):
                    cv.alpha[2:h - 2, x] = 0
            if boot and h > 8:
                cv.alpha[0:h // 2, :] = 0
                cv.fill(m_rect(w, h, 0, h // 2, w - 1, h // 2), RAMPS[trim][4])
        return cv
    return paint


def armor_sets():
    specs = {
        'hush': ('indigo', 'bone', True, False),
        'grave': ('mute', 'iron', False, False),
        'echo': ('echo', 'amethyst', True, True),
        'scar': ('scar', 'ember', False, False),
    }
    out = {}
    for name, (mat, trim, visor, lattice) in specs.items():
        human = ArmorSheet(name + '_human')
        human.box(0, 0, 8, 8, 8, _plate(mat, trim, visor=visor, lattice=lattice))
        human.box(32, 0, 8, 8, 8, _plate(mat, trim, visor=visor, lattice=lattice))
        human.box(16, 16, 8, 12, 4, _plate(mat, trim, lattice=lattice))
        human.box(40, 16, 4, 12, 4, _plate(mat, trim))
        human.box(0, 16, 4, 12, 4, _plate(mat, trim, boot=True))
        legs = ArmorSheet(name + '_legs')
        legs.box(0, 16, 4, 12, 4, _plate(mat, trim))
        def waist(f, w, h, rng, mat=mat, trim=trim):
            cv = surface(mat, w, h, rng, face='side', wear=0.4)
            if f in ('north', 'south', 'left', 'right'):
                cv.alpha[0:int(h * 0.62), :] = 0
                cv.fill(m_rect(w, h, 0, int(h * 0.62), w - 1, int(h * 0.62)), RAMPS[trim][5])
            return cv
        legs.box(16, 16, 8, 12, 4, waist)
        out[name] = (human.image(), legs.image())
    return out


# ---------------------------------------------------------------- item icons

def _icon_fiber():
    rng = core.rng_for('hush_fiber')
    cv = new()
    for i, x in enumerate((7, 13, 19)):
        shapes.rod(cv, (x, 6), (x + (i - 1) * 3, 27), 2.2, 'indigo', 4, rng=rng, noise=0.25)
    shapes.rod(cv, (8, 8), (24, 11), 1.4, 'ink', 3)
    cv.fill(m_ellipse(S, S, 16, 16, 2.0, 2.0), RAMPS['bone'][5])
    return finish(cv)


def _icon_scale():
    cv = new()
    shapes.gem(cv, [(16, 3), (28, 16), (16, 29), (4, 16)], 'mute', 3)
    shapes.gem(cv, [(16, 8), (23, 16), (16, 24), (9, 16)], 'bone', 4)
    cv.px(16, 7, RAMPS['bone'][6])
    cv.fill(m_line(S, S, (16, 9), (16, 23)), RAMPS['iron'][2])
    return finish(cv)


def _icon_sinew():
    rng = core.rng_for('scar_sinew')
    cv = new()
    shapes.rod(cv, (6, 6), (26, 26), 3.2, 'leather', 3, rng=rng, noise=0.3)
    shapes.rod(cv, (24, 7), (8, 26), 2.0, 'scar', 4, rng=rng, noise=0.2)
    shapes.glow(cv, 16, 16, 4, RAMPS['ember'][5], 0.25)
    return finish(cv)


def _icon_bolt():
    cv = new()
    shapes.rod(cv, (16, 28), (16, 12), 2.4, 'paper', 4)
    shapes.gem(cv, [(16, 2), (21, 12), (16, 10), (11, 12)], 'amethyst', 4)
    core.glint(cv, 16, 5, arm=0)
    for dx in (-3, 3):
        shapes.rod(cv, (16, 14), (16 + dx, 20), 1.2, 'paper', 5)
    return finish(cv)


def _blade_icon():
    rng = core.rng_for('recall_blade_icon')
    cv = new()
    shapes.rod(cv, (5, 28), (12, 20), 3.6, 'leather', 3, rng=rng, noise=0.2)
    cv.part(m_ellipse(S, S, 4.2, 28.6, 2.2, 2.2), 'brass', 'sphere', 3)
    shapes.rod(cv, (10, 22), (18, 18), 5.2, 'iron', 3)
    shapes.rod(cv, (14, 20), (24, 6), 2.8, 'iron', 4)
    shapes.rod(cv, (15, 18), (23, 7), 1.0, 'amethyst', 5)
    shapes.gem(cv, [(22, 8), (27, 2), (29, 4), (24, 10)], 'amethyst', 5)
    core.glint(cv, 26, 4, arm=0)
    shapes.glow(cv, 26, 4, 4, RAMPS['amethyst'][6], 0.28)
    return finish(cv)


def _maul_icon():
    rng = core.rng_for('grave_maul_icon')
    cv = new()
    shapes.rod(cv, (16, 30), (16, 16), 3.4, 'wood', 3, rng=rng, noise=0.25)
    shapes.rod(cv, (16, 26), (16, 20), 4.2, 'leather', 2)
    cv.part(m_poly(S, S, [(4, 14), (8, 4), (24, 4), (28, 14), (24, 16), (8, 16)]), 'mute', 'box', 3, rng=rng, noise=0.12)
    cv.fill(m_rect(S, S, 6, 5, 26, 7), RAMPS['iron'][5])
    cv.fill(m_rect(S, S, 6, 13, 26, 15), RAMPS['iron'][2])
    cv.fill(m_rect(S, S, 13, 6, 19, 14), RAMPS['bone'][4])
    shapes.gem(cv, [(26, 8), (30, 10), (26, 12), (24, 10)], 'iron', 4)
    cv.px(8, 6, RAMPS['iron'][6])
    return finish(cv)


def _spear_icon():
    rng = core.rng_for('hush_spear_icon')
    cv = new()
    shapes.rod(cv, (16, 31), (16, 12), 2.4, 'wood', 3, rng=rng, noise=0.2)
    shapes.rod(cv, (16, 18), (16, 13), 3.6, 'indigo', 3)
    shapes.gem(cv, [(16, 2), (22, 12), (16, 10), (10, 12)], 'bone', 5)
    cv.fill(m_line(S, S, (16, 3), (16, 11)), RAMPS['indigo'][5])
    cv.fill(m_line(S, S, (13, 8), (16, 3)), RAMPS['bone'][6])
    cv.fill(m_line(S, S, (19, 8), (16, 3)), RAMPS['bone'][6])
    return finish(cv)


def _sling_icon():
    cv = new()
    shapes.rod(cv, (16, 30), (16, 18), 3.2, 'wood', 3)
    shapes.rod(cv, (16, 26), (16, 20), 3.8, 'leather', 2)
    shapes.rod(cv, (8, 8), (15, 16), 2.2, 'leather', 3)
    shapes.rod(cv, (24, 8), (17, 16), 2.2, 'leather', 3)
    cv.part(m_ellipse(S, S, 16, 16, 5.0, 3.4), 'echo', 'sphere', 4)
    shapes.gem(cv, [(16, 12), (19, 16), (16, 19), (13, 16)], 'amethyst', 5)
    core.glint(cv, 16, 14, arm=0)
    cv.fill(m_line(S, S, (9, 8), (23, 8)), RAMPS['leather'][4])
    return finish(cv)


def _brand_icon():
    cv = new()
    shapes.rod(cv, (16, 30), (16, 18), 3.8, 'ink', 2)
    cv.part(m_rect(S, S, 11, 26, 21, 30), 'iron', 'box', 2)
    shapes.gem(cv, [(16, 2), (24, 14), (16, 12), (8, 14)], 'scar', 4)
    shapes.rod(cv, (20, 12), (22, 4), 1.3, 'ember', 5)
    shapes.glow(cv, 16, 7, 6, RAMPS['ember'][5], 0.45)
    core.glint(cv, 18, 5, arm=0)
    return finish(cv)


def _armor_icon(kind, mat, trim):
    rng = core.rng_for(kind + mat)
    cv = new()
    if kind == 'helmet':
        m = m_poly(S, S, [(8, 20), (8, 10), (12, 6), (20, 6), (24, 10), (24, 20), (20, 24), (12, 24)])
        cv.part(m, mat, 'box', 3, rng=rng, noise=0.15)
        cv.alpha[12:16, 11:21] = 0
        cv.fill(m_rect(S, S, 11, 11, 21, 12), RAMPS[trim][5])
        cv.fill(m_rect(S, S, 10, 20, 22, 21), RAMPS[trim][3])
    elif kind == 'chestplate':
        m = m_poly(S, S, [(10, 8), (14, 5), (18, 5), (22, 8), (24, 12), (22, 26), (10, 26), (8, 12)])
        cv.part(m, mat, 'box', 3, rng=rng, noise=0.15)
        cv.fill(m_rect(S, S, 6, 8, 10, 18), RAMPS[mat][2])
        cv.fill(m_rect(S, S, 22, 8, 26, 18), RAMPS[mat][4])
        cv.fill(m_rect(S, S, 14, 10, 18, 18), RAMPS[trim][4])
    elif kind == 'leggings':
        cv.part(m_rect(S, S, 10, 6, 22, 14), mat, 'box', 3)
        cv.part(m_rect(S, S, 10, 14, 15, 28), mat, 'box', 2)
        cv.part(m_rect(S, S, 17, 14, 22, 28), mat, 'box', 4)
        cv.fill(m_rect(S, S, 10, 13, 22, 15), RAMPS[trim][4])
    else:
        cv.part(m_poly(S, S, [(8, 16), (14, 16), (14, 22), (16, 22), (14, 28), (8, 28)]), mat, 'box', 3)
        cv.part(m_poly(S, S, [(18, 16), (24, 16), (24, 28), (18, 28), (16, 22), (18, 22)]), mat, 'box', 4)
        cv.fill(m_rect(S, S, 8, 16, 24, 18), RAMPS[trim][5])
    return finish(cv, selout=0.5)


ICONS = {
    'hush_fiber': _icon_fiber,
    'grave_scale': _icon_scale,
    'scar_sinew': _icon_sinew,
    'memory_bolt': _icon_bolt,
    'recall_blade': _blade_icon,
    'grave_maul': _maul_icon,
    'hush_spear': _spear_icon,
    'chorus_sling': _sling_icon,
    'scar_brand': _brand_icon,
}
for set_name, mat, trim in (
        ('hush', 'indigo', 'bone'),
        ('grave', 'mute', 'iron'),
        ('echo', 'echo', 'amethyst'),
        ('scar', 'scar', 'ember')):
    for part in ('helmet', 'chestplate', 'leggings', 'boots'):
        ICONS[f'{set_name}_{part}'] = (lambda part=part, mat=mat, trim=trim: _armor_icon(part, mat, trim))

ICONS['ledger_mite_spawn_egg'] = lambda: _egg('ledger_mite_spawn_egg', 'bone', 'indigo', 3, band='leather')
ICONS['kin_witness_spawn_egg'] = lambda: _egg('kin_witness_spawn_egg', 'indigo', 'bone', 4, band='brass')
ICONS['fracture_stalker_spawn_egg'] = lambda: _egg('fracture_stalker_spawn_egg', 'deep', 'scar', 4, band='ember')


# ---------------------------------------------------------------- weapons in the hand

def _weapon(name, build):
    m = Model(name + '_model', folder='item', seed=name + '3d')
    build(m)
    return m.build(parent=None, display=HANDHELD, texture_name=name + '_model')


def recall_blade_hand():
    """Flat blade, thin through Z so the hand sees the face, wide across X. -45 lays it on the sprite diagonal."""
    def build(m):
        rot = {'origin': [8, 8, 8], 'axis': 'z', 'angle': -45}
        m.box((7.05, 0.3, 7.15), (8.95, 1.7, 8.85), F('brass', wear=0.25), rotation=rot)
        m.box((7.25, 1.6, 7.3), (8.75, 5.5, 8.7), P(grip, 'grip'), rotation=rot)
        m.box((5.1, 5.2, 7.35), (10.9, 6.5, 8.65), F('iron', wear=0.35), rotation=rot)
        m.box((6.55, 6.3, 7.62), (9.45, 12.4, 8.38), P(shaft, 'blade'), rotation=rot)
        m.box((7.15, 12.1, 7.68), (8.85, 14.4, 8.32), P(shaft, 'blade_tip'), rotation=rot)
        m.box((7.55, 6.7, 7.32), (8.2, 13.2, 7.68), F('amethyst', wear=0.0, shift=0.8), rotation=rot)
        m.box((7.55, 6.7, 8.32), (8.2, 13.2, 8.68), F('amethyst', wear=0.0, shift=0.8), rotation=rot)
        m.box((7.5, 14.1, 7.55), (8.5, 15.6, 8.45), P(crystal, 'tip'), rotation=rot)
    return _weapon('recall_blade', build)


def grave_maul_hand():
    def build(m):
        rot = {'origin': [8, 8, 8], 'axis': 'z', 'angle': -45}
        m.box((7.1, 0.3, 7.1), (8.9, 8.4, 8.9), F('wood', wear=0.55), rotation=rot)
        m.box((6.95, 2.0, 6.95), (9.05, 5.6, 9.05), P(grip, 'grip'), rotation=rot)
        m.box((4.6, 8.5, 6.5), (11.4, 13.8, 9.5), F('mute', wear=0.65), rotation=rot)
        m.box((4.6, 8.5, 6.5), (11.4, 9.6, 9.5), F('iron', wear=0.4), rotation=rot)
        m.box((4.6, 12.7, 6.5), (11.4, 13.8, 9.5), F('iron', wear=0.45), rotation=rot)
        m.box((6.5, 9.7, 6.15), (9.5, 12.5, 6.7), F('bone', wear=0.2), rotation=rot)
        m.box((6.5, 9.7, 9.3), (9.5, 12.5, 9.85), F('bone', wear=0.2), rotation=rot)
        m.box((11.1, 10.1, 7.2), (13.2, 12.3, 8.8), F('iron', wear=0.3, shift=0.35), rotation=rot)
        m.box((2.8, 10.1, 7.2), (4.9, 12.3, 8.8), F('iron', wear=0.3, shift=0.35), rotation=rot)
    return _weapon('grave_maul', build)


def hush_spear_hand():
    def build(m):
        rot = {'origin': [8, 8, 8], 'axis': 'z', 'angle': -45}
        m.box((7.4, 0.15, 7.4), (8.6, 12.3, 8.6), F('wood', wear=0.45), rotation=rot)
        m.box((7.1, 6.4, 7.1), (8.9, 8.9, 8.9), F('indigo', wear=0.25), rotation=rot)
        m.box((7.05, 11.5, 7.15), (8.95, 13.1, 8.85), F('bone', wear=0.3), rotation=rot)
        m.box((6.15, 12.5, 7.68), (9.85, 15.5, 8.32), F('bone', wear=0.1, shift=0.55), rotation=rot)
        m.box((7.05, 15.1, 7.72), (8.95, 17.15, 8.28), F('bone', wear=0.05, shift=0.9), rotation=rot)
        m.box((7.65, 12.7, 8.15), (8.35, 16.5, 8.48), F('indigo', wear=0.05, shift=0.4), rotation=rot)
    return _weapon('hush_spear', build)


def chorus_sling_hand():
    """Handle on the sprite diagonal; the arms step off it by a legal 22.5 so the fork is a Y, not a window."""
    def build(m):
        shaft = {'origin': [8, 8, 8], 'axis': 'z', 'angle': -45}
        left = {'origin': [8, 8, 8], 'axis': 'z', 'angle': -22.5}
        right = {'origin': [8, 8, 8], 'axis': 'z', 'angle': 22.5}
        m.box((7.2, 1.0, 7.3), (8.8, 8.0, 8.7), F('wood', wear=0.4), rotation=shaft)
        m.box((7.05, 2.0, 7.15), (8.95, 5.6, 8.85), P(grip, 'grip'), rotation=shaft)
        m.box((6.35, 7.6, 7.4), (8.05, 13.4, 8.6), F('leather', wear=0.45), rotation=left)
        m.box((7.95, 7.6, 7.4), (9.65, 13.4, 8.6), F('leather', wear=0.45), rotation=right)
        m.box((6.6, 7.0, 7.15), (9.4, 8.7, 8.85), F('echo', wear=0.15, shift=0.3), rotation=shaft)
        m.box((7.2, 7.15, 7.3), (8.8, 8.9, 8.7), P(crystal, 'bead'), rotation=shaft)
    return _weapon('chorus_sling', build)


def scar_brand_hand():
    def build(m):
        rot = {'origin': [8, 8, 8], 'axis': 'z', 'angle': -45}
        m.box((7.1, 0.3, 7.15), (8.9, 1.75, 8.85), F('iron', wear=0.4), rotation=rot)
        m.box((7.2, 1.55, 7.25), (8.8, 6.15, 8.75), F('ink', wear=0.45), rotation=rot)
        m.box((6.3, 5.7, 7.2), (9.7, 7.15, 8.8), F('iron', wear=0.35), rotation=rot)
        m.box((6.35, 6.9, 7.55), (9.65, 12.6, 8.45), F('scar', wear=0.12, shift=0.35), rotation=rot)
        m.box((6.9, 12.2, 7.6), (9.1, 14.5, 8.4), F('scar', wear=0.05, shift=0.75), rotation=rot)
        m.box((7.55, 7.2, 7.28), (8.2, 13.4, 7.62), F('ember', wear=0.0, shift=0.7), rotation=rot)
        m.box((7.55, 7.2, 8.38), (8.2, 13.4, 8.72), F('ember', wear=0.0, shift=0.7), rotation=rot)
        m.box((7.3, 14.2, 7.5), (8.7, 15.7, 8.5), P(crystal, 'brand'), rotation=rot)
    return _weapon('scar_brand', build)


HANDS = {
    'recall_blade': recall_blade_hand,
    'grave_maul': grave_maul_hand,
    'hush_spear': hush_spear_hand,
    'chorus_sling': chorus_sling_hand,
    'scar_brand': scar_brand_hand,
}


def select_item(name):
    return {'model': {
        'type': 'minecraft:select', 'property': 'minecraft:display_context',
        'cases': [{'when': ['gui', 'ground', 'fixed', 'on_shelf'],
                   'model': {'type': 'minecraft:model', 'model': 'mnemolith:item/' + name}}],
        'fallback': {'type': 'minecraft:model', 'model': 'mnemolith:item/%s_in_hand' % name},
    }}


def main():
    save('textures/entity/ledger_mite.png', ledger_mite())
    save('textures/entity/kin_witness.png', kin_witness())
    save('textures/entity/fracture_stalker.png', _stalker('fracture_stalker', False))
    save('textures/entity/fracture_stalker_elite.png', _stalker('fracture_stalker_elite', True))
    for name, (human, legs) in armor_sets().items():
        save('textures/entity/equipment/humanoid/%s.png' % name, human)
        save('textures/entity/equipment/humanoid_leggings/%s.png' % name, legs)
    for name, fn in ICONS.items():
        save('textures/item/%s.png' % name, fn())
    for name, fn in HANDS.items():
        img, model = fn()
        save('textures/item/%s.png' % model['textures']['sheet'].split('/')[-1], img)
        path = os.path.join(ASSETS, 'models', 'item', name + '_in_hand.json')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as fh:
            fh.write(dump(model))
        item = os.path.join(ASSETS, 'items', name + '.json')
        with open(item, 'w', encoding='utf-8') as fh:
            fh.write(dump(select_item(name)))
    print('armory art written')


if __name__ == '__main__':
    main()
