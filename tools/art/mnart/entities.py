"""Entity textures at 128x128 on the models' 64x64 UV layout (LayerDefinition stays 64x64; the game normalises
UVs by that size, so a 128px image is simply twice the texel density). Faces follow ModelPart.Cube: north is the
front. Painted as seen from outside, top of each face up."""
import numpy as np
from PIL import Image

from . import core, shapes
from .core import Canvas, m_rect, m_ellipse, m_line, m_poly
from .materials import surface, rivet
from .palette import RAMPS, mix, OUTLINE

K = 2  # texels per model unit


def regions(u, v, w, h, d):
    return {
        'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h),
    }


class Sheet:
    def __init__(self, name):
        self.img = Image.new('RGBA', (64 * K, 64 * K), (0, 0, 0, 0))
        self.rng = core.rng_for(name)

    def box(self, u, v, w, h, d, painter):
        """painter(face, w_px, h_px, rng) -> Canvas or None (left transparent)."""
        for f, (x, y, fw, fh) in regions(u, v, w, h, d).items():
            cv = painter(f, fw * K, fh * K, self.rng)
            if cv is not None:
                self.img.alpha_composite(cv.image(), (x * K, y * K))

    def image(self):
        return self.img


def cloth(mat, face, w, h, rng, shift=0.0, wear=0.5):
    kind = 'top' if face == 'up' else 'bottom' if face == 'down' else 'side'
    return surface(mat, w, h, rng, face=kind, wear=wear, base_shift=shift)


# ------------------------------------------------------------------ archivist

def archivist():
    s = Sheet('archivist')

    def body(f, w, h, rng):
        cv = cloth('indigo', f, w, h, rng)
        if f in ('north', 'south', 'left', 'right'):
            # robe folds, a bone belt with a brass buckle, a leather strap across the chest (front)
            for x in range(2, w, 5):
                cv.shade_px(m_rect(w, h, x, 0, x, h - 1), 0.25)
                cv.shade_px(m_rect(w, h, x + 1, 0, x + 1, h - 1), 0.12, toward=(220, 225, 255))
            cv.fill(m_rect(w, h, 0, 12, w - 1, 14), RAMPS['bone'][3])
            cv.fill(m_rect(w, h, 0, 12, w - 1, 12), RAMPS['bone'][5])
            if f == 'north':
                cv.fill(m_rect(w, h, w // 2 - 2, 11, w // 2 + 1, 15), RAMPS['brass'][3])
                cv.px(w // 2 - 2, 11, RAMPS['brass'][6])
                cv.fill(m_line(w, h, (2, 0), (w - 3, 11)), RAMPS['leather'][2])
                cv.fill(m_line(w, h, (3, 0), (w - 2, 11)), RAMPS['leather'][4])
            core.grime(cv, m_rect(w, h, 0, 14, w - 1, h - 1), rng, 0.4, t=0.25)
        return cv

    def hood(f, w, h, rng):
        cv = cloth('indigo', f, w, h, rng, shift=0.3)
        if f == 'north':   # the hood opening: a void with a lit inner rim, no face in it
            cv.fill(m_rect(w, h, 2, 2, w - 3, h - 1), RAMPS['ink'][0])
            cv.fill(m_rect(w, h, 2, 2, w - 3, 2), RAMPS['indigo'][5])
            cv.fill(m_rect(w, h, 2, 2, 2, h - 1), RAMPS['indigo'][4])
            cv.fill(m_rect(w, h, w - 3, 2, w - 3, h - 1), RAMPS['indigo'][2])
            cv.fill(m_rect(w, h, 4, 5, w - 5, h - 1), mix(RAMPS['ink'][0], (0, 0, 0), 0.5))
        if f == 'up':
            cv.fill(m_line(w, h, (w // 2, 0), (w // 2, h - 1)), RAMPS['indigo'][1])
        if f == 'south':
            cv.fill(m_poly(w, h, [(w // 2 - 2, h - 1), (w // 2, h - 5), (w // 2 + 2, h - 1)]), RAMPS['indigo'][2])
        return cv

    def cloak(f, w, h, rng):
        cv = cloth('ink', f, w, h, rng, shift=1.0, wear=0.6)
        if f in ('south', 'north'):
            for x in range(1, w, 4):
                cv.shade_px(m_rect(w, h, x, 0, x, h - 1), 0.3)
            for x in range(w):   # tattered hem (cutout)
                cut = rng.choice((0, 0, 1, 2, 3))
                for y in range(h - cut, h):
                    cv.alpha[y, x] = 0
            cv.fill(m_rect(w, h, 0, 0, w - 1, 1), RAMPS['bone'][3])
        return cv

    def satchel(f, w, h, rng):
        cv = cloth('leather', f, w, h, rng, wear=0.8)
        if f == 'up':   # slips stuck in the flap
            cv.fill(m_rect(w, h, 1, 1, 2, h - 2), RAMPS['paper'][4])
            cv.fill(m_rect(w, h, 3, 1, 4, h - 2), RAMPS['echo'][4])
        if f in ('north', 'south', 'left', 'right'):
            cv.fill(m_rect(w, h, 0, 0, w - 1, 2), RAMPS['leather'][2])
            cv.fill(m_rect(w, h, w // 2 - 1, 2, w // 2, 4), RAMPS['brass'][4])
        return cv

    def arm(f, w, h, rng):
        cv = cloth('indigo', f, w, h, rng, shift=-0.4)
        if f in ('north', 'south', 'left', 'right'):
            cv.fill(m_rect(w, h, 0, h - 5, w - 1, h - 1), RAMPS['bone'][3])   # wrapped hands
            for y in range(h - 5, h, 2):
                cv.fill(m_rect(w, h, 0, y, w - 1, y), RAMPS['bone'][2])
            cv.fill(m_rect(w, h, 0, h - 7, w - 1, h - 6), RAMPS['indigo'][1])
        if f == 'down':
            cv = cloth('bone', f, w, h, rng, shift=-0.5)
        return cv

    def leg(f, w, h, rng):
        cv = cloth('ink', f, w, h, rng, shift=0.5)
        if f in ('north', 'south', 'left', 'right'):
            cv.fill(m_rect(w, h, 0, h - 6, w - 1, h - 1), RAMPS['leather'][2])
            cv.fill(m_rect(w, h, 0, h - 6, w - 1, h - 6), RAMPS['leather'][4])
        return cv

    s.box(0, 16, 10, 10, 6, body)
    s.box(0, 0, 8, 6, 8, hood)
    s.box(0, 34, 12, 14, 2, cloak)
    s.box(32, 0, 3, 5, 3, satchel)
    s.box(32, 16, 2, 9, 2, arm)
    s.box(32, 28, 2, 8, 2, leg)
    return s.image()


# ------------------------------------------------------------------ echo strider

def echo_strider():
    s = Sheet('echo_strider')

    def body(f, w, h, rng):
        cv = surface('verdigris', w, h, rng, face='top' if f == 'up' else 'side', wear=0.2, base_shift=0.3)
        if f in ('north', 'south', 'up'):
            for x in range(3, w, 6):   # bone ridges along the back
                cv.fill(m_rect(w, h, x, 0, x + 1, h - 1), RAMPS['bone'][3])
                cv.fill(m_rect(w, h, x, 0, x, h - 1), RAMPS['bone'][5])
        if f in ('north', 'south'):
            cv.fill(m_rect(w, h, 0, h - 2, w - 1, h - 1), RAMPS['verdigris'][1])
            for x in range(1, w, 7):
                cv.px(x + rng.randrange(3), rng.randrange(2, h - 2), RAMPS['gel'][6])
        return cv

    def head(f, w, h, rng):
        if f == 'north':   # blank bone plate, a single shallow groove; no eyes
            cv = surface('bone', w, h, rng, wear=0.5, base_shift=0.3)
            cv.fill(m_rect(w, h, 2, h // 2, w - 3, h // 2), RAMPS['bone'][2])
            cv.fill(m_rect(w, h, 2, h // 2 + 1, w - 3, h // 2 + 1), RAMPS['bone'][5])
            return cv
        return surface('verdigris', w, h, rng, wear=0.2)

    def fin(f, w, h, rng):
        cv = surface('gel', w, h, rng, wear=0, base_shift=-0.5)
        if f in ('left', 'right'):
            for x in range(1, w, 3):
                cv.fill(m_line(w, h, (x, h - 1), (min(w - 1, x + 2), 0)), RAMPS['bone'][4])
            for x in range(w):
                if rng.random() < 0.4:
                    cv.alpha[h - 1, x] = 0
        return cv

    def leg(f, w, h, rng):
        cv = surface('bone', w, h, rng, wear=0.4, base_shift=-0.3)
        if f in ('north', 'south', 'left', 'right'):
            for y in (h // 3, 2 * h // 3):
                cv.fill(m_rect(w, h, 0, y, w - 1, y + 1), RAMPS['verdigris'][2])
        return cv

    s.box(0, 0, 20, 4, 6, body)
    s.box(0, 12, 6, 5, 3, head)
    s.box(0, 22, 1, 3, 8, fin)
    s.box(20, 22, 1, 3, 8, fin)
    s.box(0, 34, 2, 8, 2, leg)
    return s.image()


# ------------------------------------------------------------------ moment replicant

def moment_replicant():
    s = Sheet('moment_replicant')

    def split_face(w, h, rng, pale=False):
        left = surface('bone', w, h, rng, wear=0.4)
        right = surface('indigo', w, h, rng, wear=0.4)
        img = left.image()
        img.paste(right.image().crop((w // 2, 0, w, h)), (w // 2, 0))
        cv = core.from_image(img)
        cv.fill(m_rect(w, h, w // 2 - 1, 0, w // 2, h - 1), RAMPS['ember'][4])
        cv.fill(m_rect(w, h, w // 2 - 1, 0, w // 2 - 1, h - 1), RAMPS['ember'][6])
        if pale:
            cv.shade_px(m_rect(w, h, 0, 0, w - 1, h - 1), 0.55, toward=(236, 238, 250))
            cv.alpha[:, :] = 255
        return cv

    def part(pale):
        def paint(f, w, h, rng):
            if f == 'north':
                return split_face(w, h, rng, pale)
            cv = surface('bone' if f in ('right',) else 'indigo', w, h, rng, face='top' if f == 'up' else 'side', wear=0.4)
            if pale:
                cv.shade_px(m_rect(w, h, 0, 0, w - 1, h - 1), 0.55, toward=(236, 238, 250))
            return cv
        return paint

    def head(pale):
        base = part(pale)

        def paint(f, w, h, rng):
            cv = base(f, w, h, rng)
            if f == 'north':   # a smooth mask: two shallow brow cuts, no eyes
                cv.fill(m_rect(w, h, 2, 4, 4, 4), RAMPS['bone'][1] if not pale else RAMPS['pale'][3])
                cv.fill(m_rect(w, h, w - 5, 4, w - 3, 4), RAMPS['indigo'][1] if not pale else RAMPS['pale'][2])
            return cv
        return paint

    s.box(0, 0, 6, 6, 6, head(False))
    s.box(0, 14, 4, 12, 3, part(False))
    s.box(16, 14, 2, 11, 2, part(False))
    s.box(28, 0, 2, 12, 2, part(False))
    s.box(32, 16, 6, 6, 6, head(True))
    s.box(32, 30, 4, 12, 3, part(True))
    s.box(48, 0, 2, 11, 2, part(True))
    return s.image()


# ------------------------------------------------------------------ residue and scar (tinted by the client)

def _frayed(mat, f, w, h, rng, fray, alpha=255, cracks=0, crack_rgb=None):
    cv = surface(mat, w, h, rng, face='top' if f == 'up' else 'side', wear=0, base_shift=0.2)
    cv.alpha[:, :] = alpha
    for y in range(h):
        for x in range(w):
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge == 0 and rng.random() < fray:
                cv.alpha[y, x] = 0
            elif edge == 1 and rng.random() < fray * 0.35:
                cv.alpha[y, x] = alpha // 2
    for _ in range(cracks):
        core.crack(cv, m_rect(w, h, 0, 0, w - 1, h - 1), rng, (rng.randrange(w), 0), h, crack_rgb)
    return cv


def residue_like(name, mat, crack_rgb=None, cracks=0, eyes=None):
    s = Sheet(name)

    def solid(fray, alpha=255, n_cracks=0):
        def paint(f, w, h, rng):
            return _frayed(mat, f, w, h, rng, fray, alpha, n_cracks, crack_rgb)
        return paint

    def head(f, w, h, rng):
        cv = _frayed(mat, f, w, h, rng, 0.0 if f == 'north' else 0.25, 255, cracks, crack_rgb)
        if f == 'north':
            if eyes:
                for x in (2, w - 4):
                    cv.fill(m_rect(w, h, x, 4, x + 1, 5), eyes)
            else:   # hollow eyes, a missing corner: a face half remembered
                for x in (2, w - 4):
                    cv.fill(m_rect(w, h, x, 4, x + 1, 6), RAMPS['pale'][0] if mat == 'pale' else RAMPS['deep'][1])
            cv.alpha[0:2, w - 3:w] = 0
        return cv

    def torso(f, w, h, rng):
        cv = _frayed(mat, f, w, h, rng, 0.35, 255, cracks + (1 if f in ('north', 'south') else 0), crack_rgb)
        if f == 'north':   # a bright seam down the chest where the memory shows through
            cv.fill(m_rect(w, h, w // 2, 2, w // 2, h - 3), (255, 255, 255))
            cv.fill(m_rect(w, h, w // 2 - 1, h // 2, w // 2 + 1, h // 2), (255, 255, 255))
        return cv

    s.box(0, 0, 6, 6, 6, head)
    s.box(0, 14, 6, 8, 3, torso)
    s.box(24, 0, 2, 9, 2, solid(0.3))
    s.box(0, 28, 4, 4, 2, solid(0.2, 230))
    s.box(0, 36, 3, 4, 2, solid(0.25, 170))
    s.box(0, 44, 2, 4, 1, solid(0.3, 110))

    def shard(f, w, h, rng):
        cv = Canvas(w, h)
        cv.fill(m_rect(w, h, 0, 0, w - 1, h - 1), (255, 255, 255))
        cv.fill(m_rect(w, h, w - 1, 0, w - 1, h - 1) | m_rect(w, h, 0, h - 1, w - 1, h - 1), RAMPS['pale'][2])
        return cv
    s.box(40, 0, 2, 3, 1, shard)
    return s.image()


def residue():
    return residue_like('residue', 'pale', crack_rgb=RAMPS['pale'][1])


def scar():
    return residue_like('scar', 'mute', crack_rgb=RAMPS['magenta'][5], cracks=2, eyes=(255, 250, 255))


# ------------------------------------------------------------------ echo silhouette (player layout, tinted)

PLAYER_BOXES = [  # u, v, w, h, d (64x64 player skin)
    (0, 0, 8, 8, 8), (32, 0, 8, 8, 8), (16, 16, 8, 12, 4), (16, 32, 8, 12, 4), (40, 16, 4, 12, 4), (40, 32, 4, 12, 4),
    (32, 48, 4, 12, 4), (48, 48, 4, 12, 4), (0, 16, 4, 12, 4), (0, 32, 4, 12, 4), (16, 48, 4, 12, 4), (0, 48, 4, 12, 4),
]


def echo_silhouette():
    img = Image.new('RGBA', (128, 128), (255, 255, 255, 255))
    cv = core.from_image(img)
    for u, v, w, h, d in PLAYER_BOXES:
        for f, (x, y, fw, fh) in regions(u, v, w, h, d).items():
            x0, y0, x1, y1 = x * K, y * K, (x + fw) * K - 1, (y + fh) * K - 1
            rim = m_rect(128, 128, x0, y0, x1, y1) & ~m_rect(128, 128, x0 + 1, y0 + 1, x1 - 1, y1 - 1)
            cv.fill(rim, (226, 226, 232))
    return cv.image()


def wanderer_skin():
    """A generic traveller for guide pages and previews only (not shipped as a skin)."""
    s = Sheet('wanderer')

    def head(f, w, h, rng):
        cv = surface('leather', w, h, rng, wear=0.2, base_shift=1.4)
        cv.fill(m_rect(w, h, 0, 0, w - 1, 4 if f != 'up' else h - 1), RAMPS['wood'][2])
        if f == 'north':
            cv.fill(m_rect(w, h, 3, 7, 5, 8), (255, 255, 255)); cv.fill(m_rect(w, h, 4, 7, 5, 8), RAMPS['ink'][3])
            cv.fill(m_rect(w, h, w - 6, 7, w - 4, 8), (255, 255, 255)); cv.fill(m_rect(w, h, w - 6, 7, w - 5, 8), RAMPS['ink'][3])
            cv.fill(m_rect(w, h, 6, 11, w - 7, 11), RAMPS['leather'][3])
        return cv

    def body(f, w, h, rng):
        cv = cloth('verdigris', f, w, h, rng, shift=-0.6)
        cv.fill(m_rect(w, h, 0, h - 8, w - 1, h - 7), RAMPS['leather'][2])
        return cv

    def arm(f, w, h, rng):
        cv = cloth('verdigris', f, w, h, rng, shift=-0.8)
        cv.fill(m_rect(w, h, 0, h - 6, w - 1, h - 1), surface('leather', 1, 1, rng).rgb[0, 0] * 0 + np.array(RAMPS['leather'][5]))
        return cv

    def leg(f, w, h, rng):
        cv = cloth('indigo', f, w, h, rng, shift=-0.4)
        cv.fill(m_rect(w, h, 0, h - 6, w - 1, h - 1), RAMPS['leather'][2])
        return cv

    s.box(0, 0, 8, 8, 8, head)
    s.box(16, 16, 8, 12, 4, body)
    s.box(40, 16, 4, 12, 4, arm)
    s.box(32, 48, 4, 12, 4, arm)
    s.box(0, 16, 4, 12, 4, leg)
    s.box(16, 48, 4, 12, 4, leg)
    return s.image()


ENTITIES = {
    'archivist': archivist,
    'echo_strider': echo_strider,
    'moment_replicant': moment_replicant,
    'residue': residue,
    'scar': scar,
    'echo_silhouette': echo_silhouette,
}
