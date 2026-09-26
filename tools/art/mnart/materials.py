"""Material library: paints a rectangular surface (a block face, a model face, an entity box face) at 2 texels per
model unit. Each material = a ramp + a grain + a wear habit, so a copper cheek on the reel and a copper band on the
needle read as the same metal."""
import numpy as np

from . import core
from .core import Canvas, m_rect, fbm, value_noise
from .palette import RAMPS, mix, SHADOW

# name -> (ramp, base index, grain kind, wear kind)
MATERIALS = {
    'iron': ('iron', 3, 'brushed', 'scratch'),
    'dark_iron': ('iron', 2, 'brushed', 'scratch'),
    'copper': ('copper', 3, 'brushed', 'patina'),
    'brass': ('brass', 3, 'brushed', 'scratch'),
    'verdigris': ('verdigris', 3, 'mottle', 'patina'),
    'bone': ('bone', 4, 'fine', 'stain'),
    'paper': ('paper', 4, 'fibre', 'stain'),
    'leather': ('leather', 3, 'pebble', 'scuff'),
    'wood': ('wood', 3, 'grain', 'scuff'),
    'gel': ('gel', 4, 'glow', None),
    'indigo': ('indigo', 3, 'weave', 'fray'),
    'ink': ('ink', 3, 'weave', 'fray'),
    'mute': ('mute', 3, 'stone', 'chip'),
    'navy': ('navy', 3, 'strata', 'chip'),
    'deep': ('deep', 3, 'stone', 'chip'),
    'amethyst': ('amethyst', 4, 'facet', None),
    'scar': ('scar', 4, 'facet', 'crack'),
    'echo': ('echo', 4, 'glow', None),
    'ember': ('ember', 4, 'glow', None),
    'glass': ('glass', 4, 'glass', None),
    'pale': ('pale', 4, 'fine', None),
}


def grain(kind, w, h, rng, tile, face):
    """Float offset added to the base index."""
    g = np.zeros((h, w))
    if kind == 'brushed':
        rows = np.array([rng.random() for _ in range(h)])
        g += ((rows - 0.5) * 0.9)[:, None] if face != 'top' else ((rows - 0.5) * 0.6)[:, None]
        g += (fbm(w, h, rng, (6, 3), (0.6, 0.4), tile) - 0.5) * 0.6
    elif kind == 'mottle':
        g += (fbm(w, h, rng, (5, 2), (0.7, 0.3), tile) - 0.5) * 2.0
    elif kind == 'fine':
        g += (fbm(w, h, rng, (6, 2), (0.6, 0.4), tile) - 0.5) * 0.9
    elif kind == 'fibre':
        g += (fbm(w, h, rng, (8, 2), (0.5, 0.5), tile) - 0.5) * 0.7
        for _ in range(max(1, w * h // 90)):
            x, y = rng.randrange(w), rng.randrange(h)
            for k in range(rng.randint(2, 4)):
                if x + k < w:
                    g[y, x + k] -= 0.6
    elif kind == 'pebble':
        g += (value_noise(w, h, 2, rng, tile) - 0.5) * 1.1 + (fbm(w, h, rng, (6,), (1,), tile) - 0.5) * 0.8
    elif kind == 'grain':
        cols = np.array([rng.random() for _ in range(w)])
        g += ((cols - 0.5) * 1.4)[None, :]
        g += (fbm(w, h, rng, (8, 3), (0.6, 0.4), tile) - 0.5) * 0.8
    elif kind == 'glow':
        ys, xs = np.mgrid[0:h, 0:w]
        d = np.sqrt(((xs + 0.5 - w / 2) / (w / 2)) ** 2 + ((ys + 0.5 - h / 2) / (h / 2)) ** 2)
        g += 1.2 - d * 1.6 + (fbm(w, h, rng, (4, 2), (0.6, 0.4), tile) - 0.5) * 0.8
    elif kind == 'weave':
        ys, xs = np.mgrid[0:h, 0:w]
        g += np.where((xs + ys) % 2 == 0, 0.35, -0.35) * np.where(ys % 2 == 0, 1, 0.6)
        g += (fbm(w, h, rng, (6, 3), (0.6, 0.4), tile) - 0.5) * 1.0
    elif kind == 'stone':
        n = fbm(w, h, rng, (8, 4, 2), (0.5, 0.3, 0.2), tile)
        g += np.round((n - 0.5) * 3.2) * 0.8
    elif kind == 'strata':
        n = fbm(w, h, rng, (8, 3), (0.6, 0.4), tile)
        ys = np.mgrid[0:h, 0:w][0]
        g += np.round((n - 0.5) * 2.4) * 0.7 + np.where((ys + np.round(n * 3)) % 7 == 0, -0.8, 0)
    elif kind == 'facet':
        ys, xs = np.mgrid[0:h, 0:w]
        a = rng.random() * 3
        g += np.floor(((xs * np.cos(a) + ys * np.sin(a)) / max(2, w / 3)) % 3) * 0.7 - 0.7
        g += (fbm(w, h, rng, (4,), (1,), tile) - 0.5) * 0.8
    elif kind == 'glass':
        ys, xs = np.mgrid[0:h, 0:w]
        g += np.where(((xs - ys) % (w + h) < 3) | ((xs - ys + w // 2) % (w + h) == 0), 2.0, 0.0)
    return g


def surface(mat, w, h, rng, face='side', bevel=1, wear=0.4, tile=False, base_shift=0.0):
    """Paints a w x h face. face: side | top | bottom | end (changes the light a little).
    bevel: 0 none, 1 = 1px lit top/left rim and dark bottom/right rim."""
    ramp, base, gk, wk = MATERIALS[mat]
    cv = Canvas(w, h)
    m = m_rect(w, h, 0, 0, w - 1, h - 1)
    b = base + base_shift + {'top': 0.6, 'bottom': -1.0, 'side': 0.0, 'end': -0.3}.get(face, 0)
    idx = np.full((h, w), float(b)) + grain(gk, w, h, rng, tile, face)
    if bevel and not tile:
        idx[0, :] += 1.0 * bevel
        idx[:, 0] += 0.6 * bevel
        idx[-1, :] -= 1.0 * bevel
        idx[:, -1] -= 0.6 * bevel
    cv.paint(m, ramp, idx, alpha=150 if gk == 'glass' else 255)
    if wear:
        apply_wear(cv, m, wk, rng, wear, tile)
    return cv


def apply_wear(cv, m, kind, rng, amount, tile=False):
    area = cv.w * cv.h
    if kind == 'scratch':
        core.scratches(cv, m, rng, n=max(1, int(area / 120 * amount)), lighten=0.3)
    elif kind == 'patina':
        core.scratches(cv, m, rng, n=max(1, int(area / 160 * amount)), lighten=0.25)
        low = m.copy() if tile else (m & ~core.erode(core.erode(m)))
        core.speckle(cv, low, rng, RAMPS['verdigris'][3], p=0.10 * amount)
        core.speckle(cv, m, rng, RAMPS['verdigris'][4], p=0.015 * amount)
    elif kind == 'stain':
        core.grime(cv, m, rng, amount=0.10 * amount, t=0.15)
    elif kind == 'scuff':
        core.scratches(cv, m, rng, n=max(1, int(area / 150 * amount)), lighten=0.2)
        core.grime(cv, m, rng, amount=0.08 * amount, t=0.2)
    elif kind == 'fray':
        core.grime(cv, m, rng, amount=0.06 * amount, t=0.18)
    elif kind == 'chip':
        for _ in range(max(1, int(area / 200 * amount))):
            x, y = rng.randrange(cv.w), rng.randrange(cv.h)
            core.crack(cv, m, rng, (x, y), rng.randint(2, 4), tuple(mix(tuple(cv.rgb[y, x]), SHADOW, 0.45)))
    elif kind == 'crack':
        for _ in range(max(1, int(area / 260 * amount))):
            core.crack(cv, m, rng, (rng.randrange(cv.w), rng.randrange(cv.h)), rng.randint(3, 6), RAMPS['magenta'][5])


def rivet(cv, x, y, ramp='iron'):
    r = RAMPS[ramp]
    cv.px(x, y, r[5]); cv.px(x + 1, y, r[3]); cv.px(x, y + 1, r[2]); cv.px(x + 1, y + 1, r[1])


def inset(cv, x0, y0, x1, y1, depth_t=0.35):
    """Recessed panel: dark top/left inner rim, lit bottom/right rim, floor slightly darker."""
    m = m_rect(cv.w, cv.h, x0, y0, x1, y1)
    cv.shade_px(m, depth_t * 0.5)
    cv.shade_px(m_rect(cv.w, cv.h, x0, y0, x1, y0) | m_rect(cv.w, cv.h, x0, y0, x0, y1), depth_t)
    cv.shade_px(m_rect(cv.w, cv.h, x0, y1, x1, y1) | m_rect(cv.w, cv.h, x1, y0, x1, y1), 0.25, toward=(255, 250, 235))


def raised(cv, x0, y0, x1, y1, t=0.3):
    m = m_rect(cv.w, cv.h, x0, y0, x1, y1)
    cv.shade_px(m_rect(cv.w, cv.h, x0, y0, x1, y0) | m_rect(cv.w, cv.h, x0, y0, x0, y1), t, toward=(255, 250, 235))
    cv.shade_px(m_rect(cv.w, cv.h, x0, y1, x1, y1) | m_rect(cv.w, cv.h, x1, y0, x1, y1), t)
    return m
