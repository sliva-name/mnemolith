"""Particle sprites, 32x32, white with a grey inner shade and a soft alpha halo: the client multiplies the colour
(MemoryParticle.setColor, Temper.rgb()), so no hue is baked in."""
import numpy as np
from PIL import Image, ImageFilter

from .core import m_rect, m_ellipse, m_poly, m_line, m_lines, m_ring, dilate, erode

S = 32


def _sprite(mask, core_mask=None, halo=2.2):
    """White body, a slightly darker lower-right edge, gaussian halo in alpha."""
    body = Image.fromarray((mask * 255).astype(np.uint8), 'L')
    glow = body.filter(ImageFilter.GaussianBlur(halo))
    a = np.maximum(np.array(glow, float) * 0.55, mask * 255.0)
    rgb = np.full((S, S, 3), 255.0)
    inner = mask & ~erode(mask)
    lr = inner & ~(np.roll(mask, 1, 0) & np.roll(mask, 1, 1))
    rgb[lr] = 205
    if core_mask is not None:
        rgb[core_mask & mask] = 255
    rgb[~mask] = 235
    return Image.fromarray(np.dstack([rgb, a]).clip(0, 255).astype(np.uint8), 'RGBA')


def star():
    m = m_poly(S, S, [(16, 3), (18.5, 13.5), (29, 16), (18.5, 18.5), (16, 29), (13.5, 18.5), (3, 16), (13.5, 13.5)])
    return _sprite(m | m_ellipse(S, S, 16, 16, 3.5, 3.5))


def graft_mote():
    m = m_poly(S, S, [(16, 5), (18, 14), (27, 16), (18, 18), (16, 27), (14, 18), (5, 16), (14, 14)])
    return _sprite(m, halo=3.2)


def dash():
    return _sprite(m_rect(S, S, 5, 14, 26, 17))


def plus():
    return _sprite(m_rect(S, S, 13, 5, 18, 26) | m_rect(S, S, 5, 13, 26, 18))


def cross():
    return _sprite(m_line(S, S, (7, 7), (24, 24), 4) | m_line(S, S, (24, 7), (7, 24), 4))


def ring():
    return _sprite(m_ring(S, S, 16, 16, 12, 8.5))


def blob():
    ys, xs = np.mgrid[0:S, 0:S]
    d = np.sqrt((xs + 0.5 - 16) ** 2 + (ys + 0.5 - 16) ** 2)
    a = np.clip(1 - d / 13, 0, 1) ** 1.3 * 255
    rgb = np.full((S, S, 3), 255.0) - (d[:, :, None] / 13 * 30).clip(0, 30)
    return Image.fromarray(np.dstack([rgb, a]).astype(np.uint8), 'RGBA')


def chevron():
    return _sprite(m_lines(S, S, [(6, 11), (16, 21), (26, 11)], 5))


def hook():
    return _sprite(m_lines(S, S, [(9, 6), (9, 22), (12, 26), (20, 26), (23, 22), (23, 17)], 4) | m_poly(S, S, [(20, 17), (26, 17), (23, 12)]))


def diamond():
    return _sprite(m_poly(S, S, [(16, 4), (27, 16), (16, 28), (5, 16)]) & ~m_poly(S, S, [(16, 11), (21, 16), (16, 21), (11, 16)]) | m_poly(S, S, [(16, 13), (19, 16), (16, 19), (13, 16)]))


PARTICLES = {
    'imprint_shimmer': star, 'graft_mote': graft_mote, 'imprint_extract': dash, 'compose_success': plus,
    'compose_fail': cross, 'pressure_warn': ring, 'mute_haze': blob, 'strider_trail': chevron,
    'archivist_snatch': hook, 'replicant_telegraph': diamond,
}
