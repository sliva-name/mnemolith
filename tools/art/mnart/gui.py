"""GUI chrome at twice the old density (the blits normalise by the old sizes, so no code changes):
panel 64x64 (nine-slice of the old 32), slot 36x36, tag icons 288x32 (nine 32x32 cells in ImprintTag order)."""
import numpy as np

from . import core
from .core import Canvas, m_rect, m_ellipse, m_poly, m_line, m_lines, m_ring, erode, dilate
from .materials import surface, rivet
from .palette import RAMPS, mix


def panel():
    rng = core.rng_for('gui_panel')
    S = 64
    cv = surface('paper', S, S, rng, bevel=0, wear=0.2)
    cv.shade_px(m_rect(S, S, 0, 0, S - 1, S - 1), 0.08)
    frame = m_rect(S, S, 0, 0, S - 1, S - 1) & ~m_rect(S, S, 8, 8, S - 9, S - 9)
    idx = core.shade(frame, 'flat', 3, rng, noise=0.3)
    cv.paint(frame, 'indigo', idx)
    cv.fill(m_rect(S, S, 0, 0, S - 1, 1) | m_rect(S, S, 0, 0, 1, S - 1), RAMPS['indigo'][5])
    cv.fill(m_rect(S, S, 0, S - 2, S - 1, S - 1) | m_rect(S, S, S - 2, 0, S - 1, S - 1), RAMPS['indigo'][1])
    band = m_rect(S, S, 3, 3, S - 4, S - 4) & ~m_rect(S, S, 5, 5, S - 6, S - 6)
    cv.fill(band, RAMPS['verdigris'][3])
    cv.fill(band & (m_rect(S, S, 3, 3, S - 4, 3) | m_rect(S, S, 3, 3, 3, S - 4)), RAMPS['verdigris'][5])
    cv.fill(m_rect(S, S, 6, 6, S - 7, 7) | m_rect(S, S, 6, 6, 7, S - 7), RAMPS['indigo'][1])
    cv.fill(m_rect(S, S, 8, 8, S - 9, 8) | m_rect(S, S, 8, 8, 8, S - 9), RAMPS['paper'][2])
    for x, y in ((2, 2), (S - 4, 2), (2, S - 4), (S - 4, S - 4)):
        rivet(cv, x, y, 'brass')
    return cv.image()


def slot():
    rng = core.rng_for('gui_slot')
    S = 36
    cv = surface('paper', S, S, rng, bevel=0, wear=0.1, base_shift=-1.0)
    rim = m_rect(S, S, 0, 0, S - 1, S - 1) & ~m_rect(S, S, 3, 3, S - 4, S - 4)
    cv.paint(rim, 'indigo', core.shade(rim, 'flat', 3))
    cv.fill(m_rect(S, S, 0, 0, S - 1, 1) | m_rect(S, S, 0, 0, 1, S - 1), RAMPS['indigo'][1])
    cv.fill(m_rect(S, S, 0, S - 2, S - 1, S - 1) | m_rect(S, S, S - 2, 0, S - 1, S - 1), RAMPS['indigo'][5])
    cv.fill(m_rect(S, S, 3, 3, S - 4, 3) | m_rect(S, S, 3, 3, 3, S - 4), RAMPS['paper'][1])
    return cv.image()


def _glyph(name):
    S = 32
    if name == 'fire':
        return m_poly(S, S, [(16, 5), (22, 13), (23, 20), (20, 25), (12, 25), (9, 20), (10, 14), (13, 17), (14, 10)])
    if name == 'fall':
        return m_lines(S, S, [(8, 8), (16, 15), (24, 8)], 4) | m_lines(S, S, [(8, 16), (16, 23), (24, 16)], 4)
    if name == 'death':
        return m_line(S, S, (9, 9), (23, 23), 5) | m_line(S, S, (23, 9), (9, 23), 5)
    if name == 'build':
        return m_rect(S, S, 8, 8, 23, 23) & ~m_rect(S, S, 12, 12, 19, 19)
    if name == 'explosion':
        m = m_ellipse(S, S, 16, 16, 3.5, 3.5)
        for a in np.linspace(0, np.pi * 2, 8, endpoint=False):
            m |= m_line(S, S, (16, 16), (16 + np.cos(a) * 10, 16 + np.sin(a) * 10), 2)
        return m
    if name == 'silence':
        return m_rect(S, S, 6, 14, 25, 18)
    if name == 'player':
        return (m_ellipse(S, S, 16, 17, 8, 8) & m_rect(S, S, 0, 0, 32, 20)) & ~(m_ellipse(S, S, 16, 17, 5, 5) & m_rect(S, S, 0, 0, 32, 20)) | m_rect(S, S, 7, 20, 24, 23)
    if name == 'redstone':
        m = np.zeros((S, S), bool)
        for x, y in ((11, 11), (21, 11), (11, 21), (21, 21)):
            m |= m_ellipse(S, S, x, y, 2.6, 2.6)
        return m
    if name == 'path':
        return m_rect(S, S, 5, 14, 13, 18) | m_rect(S, S, 18, 14, 26, 18)
    raise KeyError(name)


TAG_ORDER = ('fire', 'fall', 'death', 'build', 'explosion', 'silence', 'player', 'redstone', 'path')   # ImprintTag order


def tags():
    img = Canvas(32 * 9, 32).image()
    for i, name in enumerate(TAG_ORDER):
        rng = core.rng_for('tag_' + name)
        cv = surface('indigo', 32, 32, rng, wear=0.2)
        cv.fill(m_rect(32, 32, 0, 0, 31, 0) | m_rect(32, 32, 0, 0, 0, 31), RAMPS['indigo'][5])
        cv.fill(m_rect(32, 32, 0, 31, 31, 31) | m_rect(32, 32, 31, 0, 31, 31), RAMPS['indigo'][1])
        g = _glyph(name)
        shadow = np.roll(np.roll(g, 1, 0), 1, 1) & ~g
        cv.fill(shadow, RAMPS['indigo'][1])
        cv.paint(g, 'bone', core.shade(g, 'grad', 4, direction=(0.4, 1), span=(0.8, -1.0)))
        img.alpha_composite(cv.image(), (i * 32, 0))
    return img


GUI = {'panel': panel, 'slot': slot, 'tags': tags}
