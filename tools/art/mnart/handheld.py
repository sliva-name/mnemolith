"""3D in-hand models for the two instruments. GUI, ground, item frame and shelf keep the flat 32x32 sprite (the
item definition selects on display_context, like the vanilla spyglass); hands get these element models. The
models lie on the same bottom-left to top-right diagonal as the sprite, so the vanilla handheld transforms apply."""
import numpy as np

from . import core
from .core import m_rect, m_ellipse, m_line
from .materials import surface, rivet
from .modelgen import Model, F, P
from .palette import RAMPS

HANDHELD = {
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
}


def grip(w, h, rng):
    cv = surface('leather', w, h, rng, wear=0.8)
    for y in range(0, h, 3):
        cv.shade_px(m_rect(w, h, 0, y, w - 1, y), 0.35)
        cv.shade_px(m_rect(w, h, 0, y + 1, w - 1, y + 1), 0.15, toward=(255, 235, 210))
    return cv


def crystal(w, h, rng):
    cv = surface('amethyst', w, h, rng, wear=0, bevel=1, base_shift=0.6)
    cv.px(0, 0, RAMPS['amethyst'][6])
    return cv


def shaft(w, h, rng):
    cv = surface('iron', w, h, rng, wear=0.5, base_shift=0.5)
    cv.fill(m_rect(w, h, 0, 0, 0, h - 1), RAMPS['iron'][6])
    return cv


def extraction_needle_in_hand():
    m = Model('extraction_needle_model', folder='item', seed='needle3d')
    rot = {'origin': [8, 8, 8], 'axis': 'z', 'angle': -45}
    m.box((7, 1, 7), (9, 2.5, 9), F('brass', wear=0.3), rotation=rot)
    m.box((7.25, 2.5, 7.25), (8.75, 8, 8.75), F('wood', wear=0.5), rotation=rot)
    for y in (3.5, 5.5):
        m.box((7.05, y, 7.05), (8.95, y + 1, 8.95), P(grip, 'grip'), rotation=rot)
    m.box((7, 8, 7), (9, 9.5, 9), F('iron', wear=0.5), rotation=rot)
    m.box((7.5, 9.5, 7.5), (8.5, 13.5, 8.5), P(shaft, 'shaft'), rotation=rot, down=None)
    m.box((7.7, 13.5, 7.7), (8.3, 15.5, 8.3), P(shaft, 'shaft_thin'), rotation=rot, down=None)
    m.box((7.4, 15.5, 7.4), (8.6, 17.5, 8.6), P(crystal, 'tip'), rotation=rot)
    return m.build(parent=None, display=HANDHELD, texture_name='extraction_needle_model')


def ring_front(w, h, rng):
    cv = surface('copper', w, h, rng, wear=0.8)
    for x in range(1, w, 3):
        cv.px(x, h // 2, RAMPS['bone'][5] if x % 2 else RAMPS['copper'][1])
    return cv


def glass(w, h, rng):
    cv = core.Canvas(w, h)
    idx = core.shade(m_rect(w, h, 0, 0, w - 1, h - 1), 'grad', 4, direction=(1, -1), span=(1.2, -1.4), bevel=0)
    cv.paint(m_rect(w, h, 0, 0, w - 1, h - 1), 'amethyst', idx, alpha=170)
    for k in range(3):
        cv.fill(m_line(w, h, (2 + k, h - 3), (w - 5 + k, 2)), RAMPS['amethyst'][6], alpha=210)
    cv.fill(m_line(w, h, (w - 4, h - 2), (w - 2, h - 4)), RAMPS['echo'][4], alpha=200)
    return cv


def chronicle_lens_in_hand():
    m = Model('chronicle_lens_model', folder='item', seed='lens3d')
    C, R, t = 10.5, 5.0, 1.5
    z0, z1 = 7.25, 8.75
    rim = F('copper', wear=0.7)
    face = P(ring_front, 'ring_front')
    m.box((C - 2, C + R - t, z0), (C + 2, C + R, z1), rim, north=face, south=face)
    m.box((C - 2, C - R, z0), (C + 2, C - R + t, z1), rim, north=face, south=face)
    m.box((C - R, C - 2, z0), (C - R + t, C + 2, z1), rim, north=face, south=face)
    m.box((C + R - t, C - 2, z0), (C + R, C + 2, z1), rim, north=face, south=face)
    off = (R - t / 2) * 0.7071
    for sx, sy in ((1, 1), (-1, -1), (-1, 1), (1, -1)):
        cx, cy = C + sx * off, C + sy * off
        ang = -45 if sx == sy else 45
        m.box((cx - 1.7, cy - t / 2, z0), (cx + 1.7, cy + t / 2, z1), rim, north=face, south=face,
              rotation={'origin': [cx, cy, 8], 'axis': 'z', 'angle': ang})
    m.box((C - 3.6, C - 3.6, 7.9), (C + 3.6, C + 3.6, 8.1), P(glass, 'glass'), up=None, down=None, east=None, west=None)
    hrot = {'origin': [4.5, 4.5, 8], 'axis': 'z', 'angle': -45}
    m.box((3.5, 7, 7), (5.5, 8.2, 9), F('copper', wear=0.5, shift=0.4), rotation=hrot)
    m.box((3.8, 2, 7.3), (5.2, 7, 8.7), P(grip, 'grip'), rotation=hrot)
    m.box((3.5, 0.8, 7), (5.5, 2, 9), F('brass', wear=0.3), rotation=hrot)
    return m.build(parent=None, display=HANDHELD, texture_name='chronicle_lens_model')


HANDHELD_MODELS = {
    'extraction_needle': extraction_needle_in_hand,
    'chronicle_lens': chronicle_lens_in_hand,
}
