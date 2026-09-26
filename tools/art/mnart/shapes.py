"""Reusable drawn forms shared by items, guide pages and entity faces: rods, rings, gems, slips, figures."""
import math

import numpy as np

from . import core
from .core import m_rect, m_ellipse, m_poly, m_line, dilate, erode, edges
from .palette import RAMPS, mix, OUTLINE, SHADOW


def rod_mask(w, h, p0, p1, width):
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy) or 1
    nx, ny = -dy / ln * width / 2, dx / ln * width / 2
    return m_poly(w, h, [(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)])


def perp_index(w, h, p0, p1, width, base=3, hi=1.8, lo=-1.6):
    """Cylinder shading across a rod: lit on the upper-left side of the axis."""
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy) or 1
    nx, ny = -dy / ln, dx / ln
    if nx + ny > 0:  # make the normal point up-left
        nx, ny = -nx, -ny
    ys, xs = np.mgrid[0:h, 0:w]
    off = ((xs + 0.5 - x0) * nx + (ys + 0.5 - y0) * ny) / max(0.5, width / 2)  # +1 = lit side
    prof = np.where(off > 0.35, hi, np.where(off > -0.1, hi * 0.45, np.where(off > -0.6, 0.0, lo)))
    return base + prof


def rod(cv, p0, p1, width, ramp, base=3, rng=None, noise=0.0, hi=1.8, lo=-1.6):
    m = rod_mask(cv.w, cv.h, p0, p1, width)
    idx = perp_index(cv.w, cv.h, p0, p1, width, base, hi, lo)
    if rng is not None and noise:
        idx = idx + (np.array([[rng.random() for _ in range(cv.w)] for _ in range(cv.h)]) - 0.5) * noise * 2
    cv.paint(m, ramp, idx)
    return m


def ring(cv, cx, cy, r_out, r_in, ramp, base=3, ry=1.0):
    m = core.m_ring(cv.w, cv.h, cx, cy, r_out, r_in, ry)
    ys, xs = np.mgrid[0:cv.h, 0:cv.w]
    ang = (xs + 0.5 - cx) * 0.7071 + (ys + 0.5 - cy) * 0.7071   # >0 lower right
    rad = np.sqrt((xs + 0.5 - cx) ** 2 + ((ys + 0.5 - cy) / ry) ** 2)
    mid = (r_out + r_in) / 2
    idx = base - np.clip(ang / max(1, r_out), -1, 1) * 1.6 + np.where(rad < mid, -0.5, 0.6)
    cv.paint(m, ramp, idx)
    return m


def gem(cv, pts, ramp, base=4, light=(-1, -1)):
    """Faceted polygon: splits into triangles around the centroid and lights each facet by its direction."""
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    full = m_poly(cv.w, cv.h, pts)
    idx = np.full((cv.h, cv.w), float(base))
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        tri = m_poly(cv.w, cv.h, [a, b, (cx, cy)])
        mx, my = (a[0] + b[0]) / 2 - cx, (a[1] + b[1]) / 2 - cy
        ln = math.hypot(mx, my) or 1
        d = (mx * light[0] + my * light[1]) / ln
        idx[tri] = base + d * 1.6
    cv.paint(full, ramp, idx)
    return full


def slip(cv, x0, y0, x1, y1, rng, ramp='paper', fold=True, torn='bottom'):
    """A paper slip with a deckled edge and a folded top-right corner."""
    w, h = cv.w, cv.h
    pts = [(x0, y0), (x1 - (4 if fold else 0), y0), (x1, y0 + (4 if fold else 0)), (x1, y1), (x0, y1)]
    m = m_poly(w, h, pts)
    if torn:
        for x in range(x0, x1 + 1):
            if rng.random() < 0.45:
                yy = y1 if torn == 'bottom' else y0
                m[yy, x] = False
    idx = core.shade(m, 'grad', 4, rng, noise=0.25, direction=(0.4, 1), span=(0.7, -0.9))
    cv.paint(m, ramp, idx)
    if fold:
        fm = m_poly(w, h, [(x1 - 4, y0), (x1 - 4, y0 + 4), (x1, y0 + 4)])
        cv.paint(fm, ramp, np.full((h, w), 2.0))
        cv.fill(m_line(w, h, (x1 - 4, y0), (x1, y0 + 4)) & fm, RAMPS[ramp][1])
    return m


def figure_mask(w, h, x, y, s=1.0):
    """A small standing figure (head, torso, legs) about 10x22 at s=1, feet at y+22."""
    k = lambda v: int(round(v * s))
    m = m_ellipse(w, h, x + k(5), y + k(3.5), 3.2 * s, 3.4 * s)
    m |= m_poly(w, h, [(x + k(1), y + k(7)), (x + k(9), y + k(7)), (x + k(10), y + k(15)), (x + k(0), y + k(15))])
    m |= m_rect(w, h, x + k(2), y + k(15), x + k(4), y + k(22))
    m |= m_rect(w, h, x + k(6), y + k(15), x + k(8), y + k(22))
    return m


def glow(cv, cx, cy, r, rgb, strength=0.6):
    """Additive soft light over opaque pixels (memory glow)."""
    ys, xs = np.mgrid[0:cv.h, 0:cv.w]
    d = np.sqrt((xs + 0.5 - cx) ** 2 + (ys + 0.5 - cy) ** 2) / r
    t = np.clip(1 - d, 0, 1) ** 1.5 * strength
    op = cv.alpha > 0
    base = cv.rgb.astype(float)
    tgt = np.array(rgb, float)
    out = base + (tgt - base) * t[:, :, None]
    cv.rgb[op] = out[op].round().astype(np.uint8)
