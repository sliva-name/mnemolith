"""Pixel canvas, masks, shading, outlines and wear marks. numpy + Pillow only; every random choice takes a seeded
random.Random so a rebuild is byte-identical."""
import random

import numpy as np
from PIL import Image, ImageDraw

from .palette import OUTLINE, SHADOW, RAMPS, mix

# ---------------------------------------------------------------- masks


def blank(w, h):
    return np.zeros((h, w), bool)


def m_rect(w, h, x0, y0, x1, y1):
    """Inclusive pixel rectangle."""
    m = blank(w, h)
    m[max(0, y0):max(0, y1 + 1), max(0, x0):max(0, x1 + 1)] = True
    return m


def m_ellipse(w, h, cx, cy, rx, ry):
    """Pixel centres inside the ellipse; cx/cy in pixel-edge coordinates (16.0 is the middle of a 32 canvas)."""
    ys, xs = np.mgrid[0:h, 0:w]
    return ((xs + 0.5 - cx) / rx) ** 2 + ((ys + 0.5 - cy) / ry) ** 2 <= 1.0


def m_ring(w, h, cx, cy, r_out, r_in, ry_scale=1.0):
    return m_ellipse(w, h, cx, cy, r_out, r_out * ry_scale) & ~m_ellipse(w, h, cx, cy, r_in, r_in * ry_scale)


def m_poly(w, h, pts):
    img = Image.new('L', (w, h), 0)
    ImageDraw.Draw(img).polygon([tuple(p) for p in pts], fill=255, outline=255)
    return np.array(img) > 0


def m_line(w, h, p0, p1, width=1):
    img = Image.new('L', (w, h), 0)
    ImageDraw.Draw(img).line([tuple(p0), tuple(p1)], fill=255, width=width)
    return np.array(img) > 0


def m_lines(w, h, pts, width=1):
    img = Image.new('L', (w, h), 0)
    ImageDraw.Draw(img).line([tuple(p) for p in pts], fill=255, width=width)
    return np.array(img) > 0


def shift(m, dx, dy):
    out = np.zeros_like(m)
    h, w = m.shape
    xs0, xs1 = max(0, -dx), min(w, w - dx)
    ys0, ys1 = max(0, -dy), min(h, h - dy)
    out[ys0 + dy:ys1 + dy, xs0 + dx:xs1 + dx] = m[ys0:ys1, xs0:xs1]
    return out


def dilate(m, diag=False):
    o = m | shift(m, 1, 0) | shift(m, -1, 0) | shift(m, 0, 1) | shift(m, 0, -1)
    if diag:
        o |= shift(m, 1, 1) | shift(m, -1, -1) | shift(m, 1, -1) | shift(m, -1, 1)
    return o


def erode(m):
    return m & shift(m, 1, 0) & shift(m, -1, 0) & shift(m, 0, 1) & shift(m, 0, -1)


def depth(m, limit=12):
    """Distance (in erosion steps) from the mask edge: 0 on the rim."""
    d = np.full(m.shape, -1, int)
    cur = m.copy()
    for i in range(limit):
        if not cur.any():
            break
        inner = erode(cur)
        d[cur & ~inner] = i
        cur = inner
    d[cur] = limit
    return d


def edges(m):
    """Rim pixels facing each direction: dict name -> bool mask (up = the pixel above is outside)."""
    return {
        'up': m & ~shift(m, 0, 1), 'down': m & ~shift(m, 0, -1),
        'left': m & ~shift(m, 1, 0), 'right': m & ~shift(m, -1, 0),
    }


# ---------------------------------------------------------------- noise


def value_noise(w, h, cell, rng, wrap=True):
    """Smooth value noise in [0, 1). With wrap the pattern tiles (block faces)."""
    gw, gh = max(1, int(np.ceil(w / cell))), max(1, int(np.ceil(h / cell)))
    grid = np.array([[rng.random() for _ in range(gw + 1)] for _ in range(gh + 1)])
    if wrap:
        grid[-1, :] = grid[0, :]
        grid[:, -1] = grid[:, 0]
    ys, xs = np.mgrid[0:h, 0:w]
    fx, fy = xs / cell, ys / cell
    x0, y0 = np.floor(fx).astype(int), np.floor(fy).astype(int)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    x0 %= gw + 1; y0 %= gh + 1
    x1, y1 = np.minimum(x0 + 1, gw), np.minimum(y0 + 1, gh)
    a = grid[y0, x0] * (1 - tx) + grid[y0, x1] * tx
    b = grid[y1, x0] * (1 - tx) + grid[y1, x1] * tx
    return a * (1 - ty) + b * ty


def fbm(w, h, rng, cells=(8, 4, 2), weights=(0.55, 0.3, 0.15), wrap=True):
    out = np.zeros((h, w))
    for cell, wt in zip(cells, weights):
        out += value_noise(w, h, cell, rng, wrap) * wt
    return out


# ---------------------------------------------------------------- shading


def shade(m, mode='flat', base=3, rng=None, noise=0.0, bevel=1.0, hi=0.3, span=None, direction=(0, 1), grain=None):
    """Returns a float ramp-index map for mask m. Light comes from the upper left.
    mode: flat | cyl_v (vertical cylinder) | cyl_h | sphere | grad (along direction) | dome."""
    h, w = m.shape
    idx = np.full((h, w), float(base))
    ys, xs = np.mgrid[0:h, 0:w]
    if not m.any():
        return idx
    if mode in ('cyl_v', 'cyl_h'):
        for line in range(h if mode == 'cyl_v' else w):
            row = m[line, :] if mode == 'cyl_v' else m[:, line]
            pos = np.nonzero(row)[0]
            if len(pos) == 0:
                continue
            a, b = pos[0], pos[-1]
            n = max(1, b - a)
            t = (np.arange(a, b + 1) - a) / n
            prof = np.where(t < hi - 0.12, 0.8 + t * 3, np.where(t < hi + 0.12, 2.2, 1.6 - (t - hi) * 4.4))
            if mode == 'cyl_v':
                idx[line, a:b + 1] = base - 1.2 + prof
            else:
                idx[a:b + 1, line] = base - 1.2 + prof
    elif mode in ('sphere', 'dome'):
        yy, xx = np.nonzero(m)
        cx, cy = xx.mean(), yy.mean()
        rx = max(1.0, (xx.max() - xx.min()) / 2 + 0.5); ry = max(1.0, (yy.max() - yy.min()) / 2 + 0.5)
        lx, ly = cx - rx * 0.35, cy - ry * 0.4
        dd = np.sqrt(((xs - lx) / rx) ** 2 + ((ys - ly) / ry) ** 2)
        idx = base + 2.0 - dd * (2.6 if mode == 'sphere' else 1.8)
    elif mode == 'grad':
        dx, dy = direction
        proj = xs * dx + ys * dy
        pm = proj[m]
        lo, hi_ = pm.min(), pm.max()
        t = (proj - lo) / max(1, hi_ - lo)
        s = span or (1.5, -1.5)
        idx = base + s[0] + (s[1] - s[0]) * t
    if grain is not None:
        idx = idx + grain
    if noise and rng is not None:
        nz = np.array([[rng.random() for _ in range(w)] for _ in range(h)])
        idx = idx + (nz - 0.5) * 2 * noise
    if bevel:
        e = edges(m)
        idx = idx + bevel * (e['up'] | e['left']) - bevel * (e['down'] | e['right'])
    return idx


def colorize(idx, ramp_name):
    r = np.array(RAMPS[ramp_name], dtype=np.uint8)
    i = np.clip(np.round(idx).astype(int), 0, len(r) - 1)
    return r[i]


# ---------------------------------------------------------------- canvas


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.rgb = np.zeros((h, w, 3), np.uint8)
        self.alpha = np.zeros((h, w), np.uint8)
        self.dark = np.zeros((h, w, 3), np.uint8)  # outline colour each pixel asks for (sel-out)

    # -- drawing
    def fill(self, m, rgb, alpha=255, dark=None):
        self.rgb[m] = rgb
        self.alpha[m] = alpha
        self.dark[m] = dark if dark is not None else mix(rgb, OUTLINE, 0.72)

    def paint(self, m, ramp_name, idx, alpha=255, dark_i=0):
        cols = colorize(idx, ramp_name)
        self.rgb[m] = cols[m]
        self.alpha[m] = alpha
        self.dark[m] = mix(RAMPS[ramp_name][dark_i], OUTLINE, 0.45)

    def part(self, m, ramp_name, mode='flat', base=3, rng=None, noise=0.0, bevel=1.0, alpha=255, ring=False, grain=None, **kw):
        """Shade and paint one part. ring draws a 1px contour around it over what is already there."""
        if ring:
            rm = dilate(m) & ~m & (self.alpha > 0)
            self.rgb[rm] = mix(RAMPS[ramp_name][0], OUTLINE, 0.5)
        idx = shade(m, mode, base, rng, noise, bevel, grain=grain, **kw)
        self.paint(m, ramp_name, idx, alpha)
        return idx

    def px(self, x, y, rgb, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.rgb[y, x] = rgb
            self.alpha[y, x] = a

    def shade_px(self, m, t, toward=SHADOW):
        """Blend masked pixels toward a colour (t=0.25 is about one ramp step)."""
        m = m & (self.alpha > 0)
        base = self.rgb[m].astype(float)
        self.rgb[m] = (base + (np.array(toward, float) - base) * t).round().astype(np.uint8)

    def opaque(self):
        return self.alpha > 0

    def outline(self, color=None, diag=False, selout=0.0):
        """Closes the silhouette with a 1px line. selout mixes in each neighbour's own dark tone."""
        o = self.opaque()
        ring = dilate(o, diag) & ~o
        ys, xs = np.nonzero(ring)
        for y, x in zip(ys, xs):
            col = color or OUTLINE
            if selout:
                for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < self.h and 0 <= xx < self.w and o[yy, xx]:
                        col = mix(col, tuple(int(v) for v in self.dark[yy, xx]), selout)
                        break
            self.rgb[y, x] = col
            self.alpha[y, x] = 255
        return ring

    def image(self):
        a = np.dstack([self.rgb, self.alpha])
        return Image.fromarray(a, 'RGBA')

    def paste(self, img, x, y):
        """Alpha-composite a PIL image (or Canvas) at x, y."""
        if isinstance(img, Canvas):
            img = img.image()
        base = self.image()
        base.alpha_composite(img.convert('RGBA'), (int(x), int(y)))
        arr = np.array(base)
        self.rgb = arr[:, :, :3].copy()
        self.alpha = arr[:, :, 3].copy()


def from_image(img):
    arr = np.array(img.convert('RGBA'))
    cv = Canvas(arr.shape[1], arr.shape[0])
    cv.rgb = arr[:, :, :3].copy()
    cv.alpha = arr[:, :, 3].copy()
    return cv


# ---------------------------------------------------------------- wear and marks


def scratches(cv, m, rng, n=3, length=(2, 4), lighten=0.35, dark=False):
    """Short diagonal scratches inside m: bright on metal, dark with dark=True."""
    inner = erode(m)
    ys, xs = np.nonzero(inner)
    if len(xs) == 0:
        return
    for _ in range(n):
        i = rng.randrange(len(xs))
        x, y = int(xs[i]), int(ys[i])
        dx = rng.choice((1, -1))
        for k in range(rng.randint(*length)):
            xx, yy = x + k * dx, y + k
            if 0 <= xx < cv.w and 0 <= yy < cv.h and inner[yy, xx]:
                col = cv.rgb[yy, xx].astype(float)
                tgt = np.array(SHADOW if dark else (255, 250, 240), float)
                cv.rgb[yy, xx] = (col + (tgt - col) * lighten).round().astype(np.uint8)


def chips(cv, m, rng, n=2):
    """Knocks a few rim pixels out of the lit edge (worn corners)."""
    e = edges(m)
    rim = (e['up'] | e['left'] | e['right']) & ~erode(m)
    ys, xs = np.nonzero(rim)
    for _ in range(min(n, len(xs))):
        i = rng.randrange(len(xs))
        cv.alpha[ys[i], xs[i]] = 0


def grime(cv, m, rng, amount=0.18, bottom=True, t=0.22):
    h = cv.h
    ys, xs = np.nonzero(m)
    for y, x in zip(ys, xs):
        p = amount * ((y / max(1, h - 1)) * 1.6 if bottom else 1.0)
        if rng.random() < p:
            col = cv.rgb[y, x].astype(float)
            cv.rgb[y, x] = (col + (np.array(SHADOW, float) - col) * t).round().astype(np.uint8)


def speckle(cv, m, rng, rgb, p=0.05):
    ys, xs = np.nonzero(m)
    for y, x in zip(ys, xs):
        if rng.random() < p:
            cv.rgb[y, x] = rgb


def glint(cv, x, y, rgb=(255, 255, 250), arm=1, arm_rgb=None):
    cv.px(x, y, rgb)
    ar = arm_rgb or mix(rgb, (200, 200, 220), 0.35)
    for k in range(1, arm + 1):
        for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
            xx, yy = x + dx, y + dy
            if 0 <= xx < cv.w and 0 <= yy < cv.h and cv.alpha[yy, xx] > 0:
                cv.rgb[yy, xx] = ar


def crack(cv, m, rng, start, steps, rgb, light=None):
    """Random-walk crack inside m; an optional lit lip under it."""
    x, y = start
    for _ in range(steps):
        if 0 <= x < cv.w and 0 <= y < cv.h and m[y, x]:
            cv.rgb[y, x] = rgb
            if light is not None and y + 1 < cv.h and m[y + 1, x]:
                cv.rgb[y + 1, x] = light
        x += rng.choice((-1, 0, 1, 1))
        y += rng.choice((0, 1, 1))


def stitch(cv, pts, rgb):
    for i, (x, y) in enumerate(pts):
        if i % 2 == 0:
            cv.px(x, y, rgb)


def upscale(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def rng_for(name):
    """Stable per-asset random source."""
    seed = 0
    for ch in name:
        seed = (seed * 131 + ord(ch)) % 2_147_483_647
    return random.Random(seed)
