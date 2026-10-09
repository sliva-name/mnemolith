"""Field guide illustrations: 35 pages at 512x256 (the screen normalises to a 256x128 art box, so this is 2x density).
Every page is an ink-paper plate with a brass-cornered frame, one small in-world diorama rendered from the real
block models and entity layouts (render3d), the real item sprites at 2x, and a few diagram marks. No text: the book
prints its words from the lang file."""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from . import core, entities, entity_models as EM, gui, items, materials, render3d as R
from .palette import RAMPS, TEMPER, OUTLINE, c, mix

W, H = 512, 256
PAGES = ('welcome', 'hour', 'loop', 'sources', 'bands', 'lens', 'needle', 'reel', 'formulas', 'fails', 'mute',
         'catalog', 'strider', 'archivist', 'replicant', 'recording', 'echoes', 'grafts', 'residues', 'storms',
         'scar', 'relay', 'vault', 'noticed', 'traces', 'offer', 'rewrite', 'remembers', 'armory', 'beasts', 'roles',
         'guard', 'world', 'places', 'hollows', 'players', 'reference')

BAND = {'calm': c('verdigris', 4), 'saturated': c('brass', 4), 'overloaded': c('ember', 3), 'fracture': c('red', 3)}
ECHO_TINT = (255, 179, 220, 208)          # EchoRenderer.SILHOUETTE_TINT
PALE = (197, 212, 240)                    # distorted silhouette and a lie's washed residue, 0xC5D4F0
ACCENT = c('verdigris', 4)
LINE = c('bone', 4)


# ------------------------------------------------------------------ shared textures for dioramas (guide only)

_ready = False


def _prepare():
    global _ready
    if _ready:
        return
    def reg(name, img):
        img = img.image() if hasattr(img, 'image') else img
        R._tex_cache['guide:' + name] = np.array(img.convert('RGBA'))
    rng = core.rng_for('guide-ground')
    top = materials.surface('verdigris', 32, 32, rng, face='top', bevel=0, wear=0.3, tile=True, base_shift=-0.6)
    reg('turf_top', top)
    side = materials.surface('leather', 32, 32, rng, face='side', bevel=0, wear=0.3, tile=True)
    sc = side
    for x in range(32):
        d = 3 + int(rng.randrange(0, 3))
        for y in range(d):
            sc.px(x, y, RAMPS['verdigris'][2 if y == d - 1 else 3 - (y % 2)])
    reg('turf_side', sc.image())
    reg('stone', materials.surface('mute', 32, 32, rng, face='side', bevel=0, wear=0.4, tile=True, base_shift=0.4))
    reg('deep', materials.surface('deep', 32, 32, rng, face='side', bevel=0, wear=0.4, tile=True, base_shift=0.6))
    reg('plank', materials.surface('wood', 32, 32, rng, face='side', bevel=1, wear=0.5, tile=True))
    reg('wanderer', entities.wanderer_skin())
    reg('white', Image.new('RGBA', (8, 8), (255, 255, 255, 255)))
    _ready = True


# ------------------------------------------------------------------ 3d helpers

def cube(tex_top, tex_side, x, y, z, tex_bottom=None, tint=None, h=1.0):
    out = []
    a, b = (x, y, z), (x + 1, y + h, z + 1)
    for f in ('up', 'down', 'north', 'south', 'east', 'west'):
        t = tex_top if f == 'up' else (tex_bottom or tex_side) if f == 'down' else tex_side
        v0 = 0 if f in ('up', 'down') else 1 - h
        uv = [(0, v0), (1, v0), (1, 1), (0, 1)]
        out.append(R.Quad(R.face_corners(f, a, b), uv, 'guide:' + t, np.array(R.NORMALS[f], float), tint))
    return out


def overlay(x, z, y, rgb, a=120):
    """A translucent colour sheet lying on a block top (pressure bands, mute radius)."""
    p = y + 0.01
    return [R.Quad([(x, p, z), (x + 1, p, z), (x + 1, p, z + 1), (x, p, z + 1)], [(0, 0)] * 4, 'guide:white',
                   np.array([0, 1.0, 0]), (*rgb, a), shade=False)]


def placed(quads, m):
    for q in quads:
        q.pts = [tuple((m @ np.array([*p, 1.0]))[:3]) for p in q.pts]
        q.normal = m[:3, :3] @ np.asarray(q.normal, float)
    return quads


def block_quads(model_id, x, y, z, yaw=0):
    m = R.load_model(model_id)
    return placed(R.model_quads(m), R.trans(x + 0.5, y, z + 0.5) @ R.rot_y(math.radians(yaw)) @ R.trans(-0.5, 0, -0.5))


def mob_quads(name, x, y, z, yaw=0, k=1.0, poses=None):
    if name == 'wanderer':
        parts, tex = EM.player(), 'guide:wanderer'
    elif name == 'echo':   # the pink x-ray silhouette layer EchoRenderer draws over the owner's skin
        parts, tex = EM.player(), 'mnemolith:entity/echo_silhouette'
    elif name == 'scar':
        parts, tex = EM.residue(), 'mnemolith:entity/scar'
    else:
        fn, tex = EM.MODELS[name]
        parts = fn()
    return placed(EM.quads(parts, tex, poses, k), R.trans(x, y, z) @ R.rot_y(math.radians(yaw)))


def item_quads(item, x, y, z, yaw=0, k=0.6):
    m = {'generated': True, 'textures': {'layer0': 'mnemolith:item/' + item}}
    return placed(R.generated_quads(m), R.trans(x, y, z) @ R.rot_y(math.radians(yaw)) @ R.scale(k) @ R.trans(-0.5, 0, -0.5))


class Diorama:
    """Isometric in-world vignette. Coordinates in blocks; the ground top sits at y=0."""

    ZOOM = 1.35   # ground vignettes; single-object renders keep their own scale

    def __init__(self, w, h, scale=30, yaw=45, pitch=30):
        _prepare()
        if scale <= 34:
            w, h, scale = int(w * self.ZOOM), int(h * self.ZOOM), scale * self.ZOOM
            w, h = min(w, W - 24), min(h, H - 20)
        self.s = R.Scene(w, h, ss=2)
        self.s.ortho_scale = scale
        self.s.view = R.rot_x(math.radians(pitch)) @ R.rot_y(math.radians(yaw))
        self.centre = (0, 0, 0)

    def add(self, quads, light='block', tint=None, bright=1.0):
        self.s.add(quads, np.eye(4), light, tint, bright)
        return self

    def ground(self, nx, nz, layers=1, top='turf_top', side='turf_side', under='stone', bands=None, skip=()):
        """skip holds (i, j) columns or (i, layer, j) single cubes to leave out (cutaways)."""
        for i in range(nx):
            for j in range(nz):
                if (i, j) in skip:
                    continue
                for L in range(layers):
                    if (i, L, j) in skip:
                        continue
                    y = -1 - L
                    t, s = (top, side) if L == 0 else (under, under)
                    self.add(cube(t, s, i - nx / 2, y, j - nz / 2))
                if bands:
                    col = bands(i, j)
                    if col:
                        self.add(overlay(i - nx / 2, j - nz / 2, 0, col, 110), light='flat')
        return self

    def render(self, cx=0.0, cy=0.0, cz=0.0):
        self.s.view = self.s.view @ R.trans(-cx, -cy, -cz)
        return self.s.render()


# ------------------------------------------------------------------ 2d page helpers

class Page:
    def __init__(self, name):
        _prepare()
        self.name = name
        self.rng = core.rng_for('guide/' + name)
        self.img = background(self.rng)
        self.d = ImageDraw.Draw(self.img)

    def paste(self, im, x, y, shadow=True):
        if shadow:
            a = np.array(im)[:, :, 3]
            sh = Image.new('RGBA', im.size, (*RAMPS['ink'][0], 0))
            sa = Image.fromarray((a * 0.55).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3))
            sh.putalpha(sa)
            self.img.alpha_composite(sh, (int(x + 3), int(y + 4)))
        self.img.alpha_composite(im, (int(x), int(y)))

    def center(self, im, cx, cy, shadow=True):
        self.paste(im, cx - im.width // 2, cy - im.height // 2, shadow)

    def sprite(self, name, cx, cy, k=2, shadow=True, src=None):
        im = src if src is not None else item_img(name)
        self.center(core.upscale(im, k), cx, cy, shadow)

    def tag(self, tag, cx, cy, k=1):
        i = gui.TAG_ORDER.index(tag)
        im = tags_img().crop((i * 32, 0, i * 32 + 32, 32))
        self.center(core.upscale(im, k), cx, cy)

    def plate(self, x0, y0, x1, y1, tone='ink'):
        """A recessed dark card that groups a step or a legend."""
        r = RAMPS[tone]
        self.d.rectangle([x0, y0, x1, y1], fill=(*r[1], 235), outline=(*OUTLINE, 255))
        self.d.line([x0 + 1, y1 - 1, x1 - 1, y1 - 1], fill=(*r[3], 255))
        self.d.line([x1 - 1, y0 + 1, x1 - 1, y1 - 1], fill=(*r[3], 255))
        self.d.line([x0 + 1, y0 + 1, x1 - 1, y0 + 1], fill=(*r[0], 255))
        self.d.line([x0 + 1, y0 + 1, x0 + 1, y1 - 1], fill=(*r[0], 255))

    def arrow(self, p0, p1, rgb=LINE, width=3, dotted=False, bend=0.0, head=8):
        pts = _curve(p0, p1, bend)
        if dotted:
            for i, p in enumerate(pts[:-4]):
                if (i // 4) % 2 == 0:
                    self.d.ellipse([p[0] - width / 2, p[1] - width / 2, p[0] + width / 2, p[1] + width / 2], fill=(*rgb, 255))
        else:
            self.d.line(pts, fill=(*OUTLINE, 255), width=width + 2, joint='curve')
            self.d.line(pts, fill=(*rgb, 255), width=width, joint='curve')
        (ax, ay), (bx, by) = pts[-5], pts[-1]
        ang = math.atan2(by - ay, bx - ax)
        tri = [(bx, by), (bx - head * math.cos(ang - 0.5), by - head * math.sin(ang - 0.5)),
               (bx - head * math.cos(ang + 0.5), by - head * math.sin(ang + 0.5))]
        self.d.polygon(tri, fill=(*rgb, 255), outline=(*OUTLINE, 255))

    def ring(self, cx, cy, r, rgb=ACCENT, width=2, dotted=False, ry=None):
        ry = ry or r
        if dotted:
            n = int(2 * math.pi * r / 7)
            for k in range(n):
                a = 2 * math.pi * k / n
                x, y = cx + r * math.cos(a), cy + ry * math.sin(a)
                self.d.ellipse([x - 1.5, y - 1.5, x + 1.5, y + 1.5], fill=(*rgb, 255))
        else:
            self.d.ellipse([cx - r, cy - ry, cx + r, cy + ry], outline=(*rgb, 255), width=width)

    def bar(self, x0, y0, x1, y1, segs, marker=None):
        """Segmented gauge: segs = [(fraction, rgb)], marker = fraction."""
        self.plate(x0 - 3, y0 - 3, x1 + 3, y1 + 3)
        x = x0
        for f, rgb in segs:
            xe = x + (x1 - x0) * f
            self.d.rectangle([x, y0, xe - 1, y1], fill=(*rgb, 255))
            self.d.line([x, y0, xe - 1, y0], fill=(*mix(rgb, (255, 255, 255), 0.35), 255))
            self.d.line([x, y1, xe - 1, y1], fill=(*mix(rgb, (0, 0, 0), 0.35), 255))
            x = xe
        if marker is not None:
            mx = x0 + (x1 - x0) * marker
            self.d.polygon([(mx, y0 - 2), (mx - 5, y0 - 9), (mx + 5, y0 - 9)], fill=(*LINE, 255), outline=(*OUTLINE, 255))

    def pip(self, cx, cy, n, rgb=ACCENT):
        """Step number as a row of dots (the book has no text in pictures)."""
        for k in range(n):
            x = cx + (k - (n - 1) / 2) * 7
            self.d.ellipse([x - 2.5, cy - 2.5, x + 2.5, cy + 2.5], fill=(*rgb, 255), outline=(*OUTLINE, 255))

    def spark(self, cx, cy, r, rgb, n=6):
        for k in range(n):
            a = self.rng.random() * 2 * math.pi
            d = r * (0.3 + 0.7 * self.rng.random())
            x, y = cx + d * math.cos(a), cy + d * math.sin(a)
            s = 1 + int(self.rng.randrange(0, 2))
            self.d.line([x - s, y, x + s, y], fill=(*rgb, 255))
            self.d.line([x, y - s, x, y + s], fill=(*rgb, 255))

    def cross(self, cx, cy, r, rgb=None):
        rgb = rgb or c('red', 4)
        for w, col in ((7, OUTLINE), (4, rgb)):
            self.d.line([cx - r, cy - r, cx + r, cy + r], fill=(*col, 255), width=w)
            self.d.line([cx - r, cy + r, cx + r, cy - r], fill=(*col, 255), width=w)

    def check(self, cx, cy, r, rgb=None):
        rgb = rgb or ACCENT
        pts = [(cx - r, cy), (cx - r / 3, cy + r * 0.7), (cx + r, cy - r * 0.7)]
        self.d.line(pts, fill=(*OUTLINE, 255), width=7, joint='curve')
        self.d.line(pts, fill=(*rgb, 255), width=4, joint='curve')

    def image(self):
        frame(self.img)
        return self.img


def _curve(p0, p1, bend, n=40):
    (x0, y0), (x1, y1) = p0, p1
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2
    nx, ny = -(y1 - y0), (x1 - x0)
    cx, cy = mx + nx * bend, my + ny * bend
    return [((1 - t) ** 2 * x0 + 2 * (1 - t) * t * cx + t * t * x1, (1 - t) ** 2 * y0 + 2 * (1 - t) * t * cy + t * t * y1)
            for t in np.linspace(0, 1, n)]


def background(rng):
    """Ink-dyed archive paper: indigo fibre, faint diagonal ruling, a soft vignette."""
    ys, xs = np.mgrid[0:H, 0:W]
    n = core.fbm(W, H, rng, cells=(64, 16, 4), weights=(0.5, 0.3, 0.2), wrap=False)
    idx = 2.4 + (n - 0.5) * 0.9
    idx += ((xs + ys) % 9 == 0) * 0.35
    vig = ((xs - W / 2) / (W / 2)) ** 2 + ((ys - H / 2) / (H / 2)) ** 2
    idx -= vig * 0.6
    fib = np.random.default_rng(rng.randrange(1 << 30)).random((H, W)) < 0.012
    idx[fib] += 0.8
    r = np.array(RAMPS['ink'], float)
    i0 = np.clip(np.floor(idx).astype(int), 0, 6)
    i1 = np.clip(i0 + 1, 0, 6)
    t = np.clip(idx - np.floor(idx), 0, 1)[:, :, None]
    rgb = r[i0] * (1 - t) + r[i1] * t
    img = np.dstack([rgb.round().astype(np.uint8), np.full((H, W), 255, np.uint8)])
    return Image.fromarray(img, 'RGBA')


def frame(img):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W - 1, H - 1], outline=(*OUTLINE, 255), width=2)
    d.rectangle([2, 2, W - 3, H - 3], outline=(*c('navy', 3), 255), width=3)
    d.rectangle([5, 5, W - 6, H - 6], outline=(*c('verdigris', 3), 255), width=1)
    d.rectangle([6, 6, W - 7, H - 7], outline=(*OUTLINE, 255), width=1)
    d.line([7, 7, W - 8, 7], fill=(*c('ink', 0), 255))
    for cx, cy in ((10, 10), (W - 11, 10), (10, H - 11), (W - 11, H - 11)):
        d.polygon([(cx - 8, cy), (cx, cy - 8), (cx + 8, cy), (cx, cy + 8)], fill=(*c('brass', 3), 255), outline=(*OUTLINE, 255))
        d.polygon([(cx - 5, cy), (cx, cy - 5), (cx, cy)], fill=(*c('brass', 5), 255))
        d.polygon([(cx + 5, cy), (cx, cy + 5), (cx, cy)], fill=(*c('brass', 2), 255))
        d.ellipse([cx - 2, cy - 2, cx + 1, cy + 1], fill=(*c('brass', 6), 255))


_items = {}
_tags = []


def item_img(name):
    if name not in _items:
        _items[name] = items.ITEMS[name]()
    return _items[name]


def tags_img():
    if not _tags:
        _tags.append(gui.tags())
    return _tags[0]


def band_of(i, j, grid):
    return BAND[grid[j][i]] if grid[j][i] else None


# ------------------------------------------------------------------ pages

def p_welcome():
    p = Page('welcome')
    d = Diorama(300, 220, 30)
    grid = [[None, 'calm', 'calm', None], ['calm', 'saturated', 'calm', None], [None, 'calm', 'overloaded', 'calm'], [None, None, 'calm', None]]
    d.ground(4, 4, 2, bands=lambda i, j: band_of(i, j, grid))
    d.add(mob_quads('wanderer', -0.6, 0, 0.4, 200))
    d.add(block_quads('mnemolith:block/mute_stone', 0.5, 0, -1.8))
    d.add(block_quads('mnemolith:block/composition_reel', -2, 0, -1.2, 90))
    p.center(d.render(0, -0.4, 0), 150, 132)
    p.spark(180, 110, 40, c('echo', 4), 10)
    p.sprite('field_guide', 330, 90, 3)
    p.sprite('chronicle_lens', 420, 88, 2)
    p.sprite('extraction_needle', 420, 160, 2)
    p.sprite('imprint_slip', 330, 175, 2)
    p.arrow((370, 90), (395, 90), dotted=True, width=3)
    p.arrow((420, 118), (420, 136), width=3)
    p.arrow((395, 170), (360, 175), width=3)
    return p.image()


def p_hour():
    p = Page('hour')
    steps = [('chronicle_lens', 1), ('extraction_needle', 2), ('imprint_slip', 3), ('composition_reel', 4)]
    for k, (it, n) in enumerate(steps):
        x = 72 + k * 122
        p.plate(x - 44, 52, x + 44, 196)
        p.pip(x, 66, n)
        if it == 'composition_reel':
            d = Diorama(90, 90, 44)
            d.add(block_quads('mnemolith:block/composition_reel', -0.5, 0, -0.5))
            p.center(d.render(0, 0.4, 0), x, 128)
        else:
            p.sprite(it, x, 128, 2)
        if k < 3:
            p.arrow((x + 48, 124), (x + 74, 124), width=3)
    p.sprite('imprint_slip', 438, 174, 1)
    p.sprite('imprint_slip', 458, 178, 1)
    p.spark(448, 110, 22, c('brass', 5))
    p.bar(40, 218, 472, 226, [(0.25, BAND['calm']), (0.25, BAND['saturated']), (0.25, BAND['overloaded']), (0.25, BAND['fracture'])], 0.18)
    return p.image()


def p_loop():
    p = Page('loop')
    d = Diorama(170, 150, 26)
    grid = [['calm', 'saturated', 'calm'], ['saturated', 'overloaded', 'saturated'], ['calm', 'saturated', 'calm']]
    d.ground(3, 3, 2, bands=lambda i, j: band_of(i, j, grid))
    p.center(d.render(0, -0.6, 0), 110, 128)
    for k in range(8):   # eight imprint slots above the chunk
        x = 60 + k * 13
        p.plate(x - 5, 40, x + 5, 50)
        if k < 6:
            p.d.rectangle([x - 3, 42, x + 3, 48], fill=(*c('echo', 4 - (k > 3)), 255))
    p.sprite('imprint_slip', 256, 90, 2)
    d = Diorama(110, 110, 50)
    d.add(block_quads('mnemolith:block/composition_reel', -0.5, 0, -0.5, 30))
    p.center(d.render(0, 0.4, 0), 400, 100)
    p.arrow((175, 110), (225, 92), bend=0.2)
    p.arrow((290, 88), (345, 96), bend=-0.15)
    p.arrow((400, 160), (170, 196), bend=-0.25, dotted=True)
    p.tag('death', 370, 205)
    p.tag('silence', 410, 205)
    return p.image()


def p_sources():
    p = Page('sources')
    # First-copy contribution ≈ weight × intensity. Two columns so fourteen tags fit the page.
    rows = [
        ('path', 1), ('build', 2), ('redstone', 2), ('trade', 2), ('player', 4),
        ('portal', 10), ('sculk', 8), ('fire', 8), ('silence', 8), ('fall', 10),
        ('lightning', 18), ('explosion', 24), ('death', 27), ('boss', 40),
    ]
    top, row_h, max_v = 28, 15, 40
    for k, (t, v) in enumerate(rows):
        col = k // 7
        row = k % 7
        x0 = 236 + col * 140
        y = top + row * row_h
        i = gui.TAG_ORDER.index(t)
        ic = tags_img().crop((i * 32, 0, i * 32 + 32, 32)).resize((14, 14), Image.NEAREST)
        p.paste(ic, x0, y - 5, shadow=False)
        tint = c('verdigris', 4) if v <= 4 else c('ember', 4) if v <= 18 else c('red', 4)
        p.plate(x0 + 18, y - 3, x0 + 132, y + 7)
        p.d.rectangle([x0 + 20, y - 1, x0 + 20 + int(110 * v / max_v), y + 5], fill=(*tint, 255))
    d = Diorama(220, 210, 26)
    d.ground(4, 4, 2)
    d.add(mob_quads('wanderer', -0.8, 0, 0.8, 215))
    d.add(block_quads('mnemolith:block/archival_stratum', 0.2, 0, -0.8))
    d.add(cube('plank', 'plank', 1.0, 0, -1.8))
    d.add(mob_quads('archivist', 1.2, 0, 1.2, 250))
    p.center(d.render(0, -0.3, 0), 118, 128)
    p.spark(118, 118, 55, c('echo', 4), 12)
    return p.image()


def p_bands():
    p = Page('bands')
    p.bar(40, 40, 472, 54, [(0.2, BAND['calm']), (0.3, BAND['saturated']), (0.3, BAND['overloaded']), (0.2, BAND['fracture'])], 0.62)
    for k, band in enumerate(('calm', 'saturated', 'overloaded', 'fracture')):
        cx = 76 + k * 120
        d = Diorama(80, 80, 16)
        d.ground(3, 3, 2, bands=lambda i, j, b=band: BAND[b] if (i + j) % 2 == 0 or b != 'calm' else None)
        if band == 'fracture':
            d.add(mob_quads('moment_replicant', 0.0, 0, 0.0, 200))
        if band == 'overloaded':
            d.add(mob_quads('echo_strider', 0.2, 0, 0.1, 230, 0.8))
        p.center(d.render(0, -0.5, 0), cx, 150)
        p.d.rectangle([cx - 30, 212, cx + 30, 218], fill=(*BAND[band], 255), outline=(*OUTLINE, 255))
    return p.image()


def p_lens():
    p = Page('lens')
    p.sprite('chronicle_lens', 110, 128, 5)
    # the lens pill as a gauge arc
    cx, cy = 340, 150
    for k, band in enumerate(('calm', 'saturated', 'overloaded', 'fracture')):
        a0, a1 = 180 + k * 45, 180 + (k + 1) * 45
        p.d.arc([cx - 100, cy - 100, cx + 100, cy + 100], a0 + 1, a1 - 1, fill=(*OUTLINE, 255), width=16)
        p.d.arc([cx - 98, cy - 98, cx + 98, cy + 98], a0 + 2, a1 - 2, fill=(*BAND[band], 255), width=12)
    a = math.radians(180 + 70)
    p.d.line([cx, cy, cx + 82 * math.cos(a), cy + 82 * math.sin(a)], fill=(*OUTLINE, 255), width=6)
    p.d.line([cx, cy, cx + 82 * math.cos(a), cy + 82 * math.sin(a)], fill=(*LINE, 255), width=3)
    p.d.ellipse([cx - 8, cy - 8, cx + 8, cy + 8], fill=(*c('brass', 4), 255), outline=(*OUTLINE, 255), width=2)
    d = Diorama(140, 100, 20)
    d.ground(3, 3, 1)
    d.add(block_quads('mnemolith:block/archival_stratum', -0.5, -1, -0.5))
    p.center(d.render(0, -0.3, 0), 340, 200)
    p.spark(340, 190, 30, c('amethyst', 5), 8)
    return p.image()


def p_needle():
    p = Page('needle')
    d = Diorama(220, 200, 34)
    d.ground(3, 3, 2)
    d.add(mob_quads('wanderer', 0.1, 0, 0.6, 190))
    p.center(d.render(0, -0.2, 0), 128, 128)
    p.sprite('extraction_needle', 128, 60, 2)
    for k in range(4):
        p.d.ellipse([120 + k * 3, 170 - k * 26, 126 + k * 3, 176 - k * 26], fill=(*c('echo', 4 + (k % 2)), 255))
    p.arrow((230, 118), (300, 118))
    p.sprite('imprint_slip', 350, 118, 3)
    p.tag('death', 430, 90)
    p.tag('player', 430, 146)
    p.d.line([398, 90, 412, 90], fill=(*LINE, 255), width=2)
    p.d.line([398, 146, 412, 146], fill=(*LINE, 255), width=2)
    return p.image()


def p_reel():
    p = Page('reel')
    d = Diorama(200, 200, 90)
    d.add(block_quads('mnemolith:block/composition_reel', -0.5, 0, -0.5, 20))
    p.center(d.render(0, 0.45, 0), 120, 128)
    s = gui.slot()
    for k, t in enumerate(('imprint_slip', 'imprint_slip')):
        x = 270 + k * 60
        p.center(s, x, 100, shadow=False)
        p.sprite(t, x, 100, 1, shadow=False)
    p.arrow((380, 100), (420, 100))
    p.center(s, 452, 100, shadow=False)
    p.spark(452, 100, 18, c('brass', 5))
    p.tag('death', 270, 160)
    p.tag('silence', 330, 160)
    p.d.line([296, 160, 304, 160], fill=(*LINE, 255), width=2)
    p.d.line([300, 156, 300, 164], fill=(*LINE, 255), width=2)
    return p.image()


def p_formulas():
    """The four patterns (CompositionFormula): a pair of tags on the reel and what the effect feels like."""
    p = Page('formulas')
    pairs = [('death', 'silence', TEMPER['silence'], None), ('fire', 'build', TEMPER['fire'], None),
             ('fall', 'player', TEMPER['fall'], None), ('silence', 'player', None, 'archivist_bait')]
    for k, (a, b, rgb, it) in enumerate(pairs):
        col, row = k % 2, k // 2
        x, y = 34 + col * 226, 44 + row * 92
        p.plate(x, y, x + 214, y + 76)
        p.tag(a, x + 34, y + 38)
        p.tag(b, x + 86, y + 38)
        p.d.line([x + 55, y + 38, x + 65, y + 38], fill=(*LINE, 255), width=2)
        p.d.line([x + 60, y + 33, x + 60, y + 43], fill=(*LINE, 255), width=2)
        p.arrow((x + 110, y + 38), (x + 142, y + 38), width=2, head=6)
        if it:
            p.sprite(it, x + 176, y + 38, 2)
        else:
            p.d.ellipse([x + 158, y + 20, x + 194, y + 56], fill=(*rgb, 255), outline=(*OUTLINE, 255), width=2)
            p.d.ellipse([x + 165, y + 25, x + 177, y + 35], fill=(*mix(rgb, (255, 255, 255), 0.55), 255))
            p.d.arc([x + 162, y + 24, x + 190, y + 52], 20, 110, fill=(*mix(rgb, (0, 0, 0), 0.3), 255), width=3)
    return p.image()


def p_fails():
    p = Page('fails')
    d = Diorama(200, 200, 90)
    d.add(block_quads('mnemolith:block/composition_reel', -0.5, 0, -0.5, -20))
    p.center(d.render(0, 0.45, 0), 120, 120)
    p.cross(120, 60, 14)
    s = gui.slot()
    for k, it in enumerate(('imprint_slip', 'imprint_slip', 'unstable_slip')):
        x = 262 + k * 50
        p.center(s, x, 80, shadow=False)
        p.sprite(it, x, 80, 1, shadow=False)
    p.cross(362, 80, 8)
    p.bar(250, 150, 470, 162, [(0.35, BAND['calm']), (0.15, BAND['saturated']), (0.5, (0, 0, 0))], 0.5)
    p.arrow((300, 186), (420, 186), rgb=c('ember', 4), width=3)
    p.sprite('unstable_slip', 130, 212, 1)
    return p.image()


def p_mute():
    p = Page('mute')
    d = Diorama(300, 230, 30)
    d.ground(5, 5, 1, bands=lambda i, j: c('mute', 5) if abs(i - 2) + abs(j - 2) <= 2 else None)
    d.add(block_quads('mnemolith:block/mute_stone', -0.5, 0, -0.5))
    d.add(block_quads('mnemolith:block/resonator_trap', 1.5, 0, -1.5))
    d.add(mob_quads('echo_strider', -1.6, 0, 1.2, 210, 0.8))
    p.center(d.render(0, -0.2, 0), 170, 130)
    d = Diorama(120, 120, 60)
    d.add(block_quads('mnemolith:block/mute_stone', -0.5, 0, -0.5))
    p.center(d.render(0, 0.5, 0), 400, 80)
    d = Diorama(120, 120, 60)
    d.add(block_quads('mnemolith:block/resonator_trap', -0.5, 0, -0.5))
    p.center(d.render(0, 0.3, 0), 400, 180)
    return p.image()


def p_catalog():
    p = Page('catalog')
    p.sprite('catalog_fragment', 100, 128, 4)
    p.plate(210, 36, 478, 220, 'navy')
    for k, t in enumerate(gui.TAG_ORDER):
        y = 50 + k * 19
        i = gui.TAG_ORDER.index(t)
        ic = tags_img().crop((i * 32, 0, i * 32 + 32, 32)).resize((16, 16), Image.NEAREST)
        p.paste(ic, 222, y - 2, shadow=False)
        known = k % 3 != 2
        p.d.rectangle([246, y + 4, 246 + (150 if known else 90), y + 8], fill=(*(c('bone', 4) if known else c('navy', 5)), 255))
        if known:
            p.d.ellipse([452, y + 1, 462, y + 11], fill=(*ACCENT, 255), outline=(*OUTLINE, 255))
        else:
            p.d.rectangle([452, y + 2, 461, y + 10], outline=(*c('navy', 5), 255))
    return p.image()


def _mob_page(name, mob, extra):
    p = Page(name)
    d = Diorama(280, 236, 40)
    d.ground(4, 3, 2)
    for q in mob:
        d.add(q)
    p.center(d.render(0, 0.1, 0), 150, 126)
    extra(p)
    return p.image()


def p_strider():
    def extra(p):
        for k in range(4):   # strider trail chevrons behind it
            x, y = 250 + k * 16, 60 + k * 9
            p.d.polygon([(x, y), (x + 8, y - 6), (x + 16, y), (x + 8, y - 3)], fill=(*c('echo', 5 - k % 2), 255), outline=(*OUTLINE, 255))
        p.sprite('echo_strider_spawn_egg', 400, 90, 3)
        p.sprite('imprint_slip', 400, 186, 2)
        p.spark(400, 186, 30, c('echo', 5))
    return _mob_page('strider', [mob_quads('echo_strider', 0.2, 0, 0, 290, 1.6)], extra)


def p_archivist():
    def extra(p):
        p.sprite('archivist_bait', 360, 80, 2)
        p.sprite('archivist_husk', 440, 80, 2)
        p.sprite('imprint_slip', 400, 180, 2)
        p.arrow((370, 180), (270, 150), bend=0.2, dotted=True)
    return _mob_page('archivist', [mob_quads('archivist', 0.5, 0, 0.1, 210, 1.5), mob_quads('wanderer', -1.3, 0, 0.4, 150)], extra)


def p_replicant():
    def extra(p):
        p.sprite('moment_replicant_spawn_egg', 410, 80, 3)
        p.d.polygon([(410, 150), (430, 172), (410, 194), (390, 172)], fill=(*c('amethyst', 4), 255), outline=(*OUTLINE, 255), width=2)
        p.d.polygon([(410, 158), (422, 172), (410, 172)], fill=(*c('amethyst', 6), 255))
    return _mob_page('replicant', [mob_quads('moment_replicant', 0.5, 0, 0, 210, 1.2), mob_quads('wanderer', -1.2, 0, 0.3, 210)], extra)


def echo_quads(x, z, yaw, poses=None, k=1.0):
    return mob_quads('echo', x, 0, z, yaw, k, poses)


def p_recording():
    p = Page('recording')
    d = Diorama(330, 220, 30)
    d.ground(6, 3, 1)
    d.add(mob_quads('wanderer', 1.9, 0, 0, 225))
    for k, x in enumerate((0.4, -1.1, -2.6)):
        d.add(echo_quads(x, 0, 225), light='entity', tint=(*ECHO_TINT[:3], 200 - k * 55))
    p.center(d.render(0, -0.1, 0), 180, 132)
    p.sprite('echo_slip', 420, 70, 2)
    p.arrow((420, 100), (420, 140))
    p.sprite('echo_recording', 420, 180, 2)
    return p.image()


def p_echoes():
    p = Page('echoes')
    d = Diorama(360, 236, 30)
    d.ground(6, 4, 2)
    d.add(cube('stone', 'stone', 1.5, 0, -1.5))
    d.add(cube('stone', 'stone', 2.5, 0, -1.5))
    d.add(echo_quads(0.8, -0.6, 250, {'right_arm': (-1.9, 0, 0.1)}), light='entity', tint=ECHO_TINT)
    d.add(echo_quads(-1.5, 0.8, 200, {'right_arm': (-0.8, 0, 0), 'left_arm': (-0.8, 0, 0)}), light='entity', tint=ECHO_TINT)
    d.add(block_quads('mnemolith:block/archive_vault', -2.8, 0, -1.8))
    p.center(d.render(0, -0.2, 0), 200, 132)
    p.sprite('echo_chorus_slip', 440, 60, 1)
    p.sprite('echo_long_slip', 440, 110, 1)
    p.sprite('echo_sturdy_slip', 440, 160, 1)
    p.sprite('echo_recording', 440, 210, 1)
    return p.image()


def p_grafts():
    p = Page('grafts')
    d = Diorama(420, 200, 30)
    d.ground(8, 2, 1)
    for k, t in enumerate(('silence', 'death', 'fire', 'fall', 'explosion')):
        d.add(echo_quads(-3.0 + k * 1.5, 0, 215), light='entity', tint=(*mix(TEMPER[t], (255, 255, 255), 0.25), 208))
    p.center(d.render(0, 0.3, 0), 256, 150)
    for k, t in enumerate(('silence', 'death', 'fire', 'fall', 'explosion')):
        x = 95 + k * 80
        p.tag(t, x, 40)
    p.sprite('imprint_slip', 470, 220, 1)
    return p.image()


def p_residues():
    p = Page('residues')
    d = Diorama(360, 230, 34)
    grid = [['overloaded', 'overloaded', 'saturated', 'calm'], ['overloaded', 'fracture', 'overloaded', 'calm'], ['saturated', 'overloaded', 'saturated', 'calm']]
    d.ground(4, 3, 2, bands=lambda i, j: band_of(i, j, grid))
    d.add(mob_quads('residue', -0.6, 0.2, 0, 220), light='entity', tint=(*TEMPER['fire'], 255))
    d.add(mob_quads('residue', 1.0, 0.4, -0.6, 200), light='entity', tint=(*TEMPER['death'], 255))
    p.center(d.render(0, 0.1, 0), 190, 126)
    p.sprite('residual_shard', 430, 90, 3)
    p.spark(430, 90, 40, TEMPER['fire'])
    p.sprite('echo_slip', 430, 190, 1)
    return p.image()


def p_storms():
    p = Page('storms')
    d = Diorama(380, 230, 26)
    grid = [['saturated', 'overloaded', 'overloaded', 'saturated', 'calm'], ['overloaded', 'fracture', 'fracture', 'overloaded', 'calm'],
            ['overloaded', 'fracture', 'overloaded', 'saturated', 'calm'], ['saturated', 'overloaded', 'saturated', 'calm', 'calm']]
    d.ground(5, 4, 1, bands=lambda i, j: band_of(i, j, grid))
    for k, (t, x, z) in enumerate((('explosion', -1.5, -1), ('fall', 0.2, 0.5), ('silence', -0.5, -2))):
        d.add(mob_quads('residue', x, 0.5 + k * 0.3, z, 200 + k * 40), light='entity', tint=(*TEMPER[t], 255))
    d.add(mob_quads('wanderer', 1.8, 0, 1.2, 225))
    p.center(d.render(0, 0.2, 0), 200, 140)
    for k in range(3):   # storm crown
        x0 = 110 + k * 70
        pts = [(x0, 30), (x0 + 12, 50), (x0 + 4, 52), (x0 + 16, 74)]
        p.d.line(pts, fill=(*OUTLINE, 255), width=5)
        p.d.line(pts, fill=(*c('scar', 5), 255), width=2)
    p.ring(200, 60, 120, c('scar', 4), dotted=True, ry=22)
    p.sprite('residual_shard', 440, 128, 2)
    return p.image()


def p_scar():
    p = Page('scar')
    d = Diorama(330, 236, 30)
    d.ground(5, 4, 2, bands=lambda i, j: BAND['fracture'] if (i + j) % 3 else None)
    d.add(cube('stone', 'stone', 1.5, 0, 0.5))
    d.add(block_quads('mnemolith:block/scar_heart', -0.5, 0, -1.5))
    d.add(mob_quads('scar', 0.2, 0.3, 0.5, 210, 2.2), light='entity')
    p.center(d.render(0, 0.6, 0), 180, 130)
    d = Diorama(90, 90, 40)
    d.add(block_quads('mnemolith:block/scar_glass', -0.5, 0, -0.5) if _has('mnemolith:block/scar_glass') else [])
    p.center(d.render(0, 0.5, 0), 420, 70)
    p.sprite('scar_fragment', 420, 180, 3)
    return p.image()


def _has(mid):
    try:
        R.load_model(mid)
        return True
    except FileNotFoundError:
        return False


def p_relay():
    p = Page('relay')
    d = Diorama(360, 220, 30)
    d.ground(7, 3, 1)
    d.add(echo_quads(-2.2, 0, 180), light='entity', tint=ECHO_TINT)
    d.add(echo_quads(2.2, 0, 250, {'right_arm': (-1.8, 0, 0)}), light='entity', tint=ECHO_TINT)
    d.add(cube('stone', 'stone', 2.6, 0, -1.4))
    p.center(d.render(0, 0.2, 0), 200, 140)
    p.arrow((130, 90), (270, 90), rgb=c('copper', 5), bend=-0.25)
    p.arrow((270, 104), (130, 104), rgb=c('copper', 3), bend=-0.25, dotted=True)
    p.sprite('relay_thread', 440, 128, 3)
    return p.image()


def p_vault():
    p = Page('vault')
    d = Diorama(260, 236, 60)
    d.ground(2, 2, 1)
    d.add(block_quads('mnemolith:block/archive_vault_on', -0.5, 0, -0.5))
    p.center(d.render(0, 0.4, 0), 150, 128)
    for k in range(5):
        x, y = 280 + k * 30, 70 + (k % 2) * 16
        p.d.rectangle([x, y, x + 10, y + 13], fill=(*c('paper', 4), 255), outline=(*OUTLINE, 255))
        p.d.line([x + 2, y + 5, x + 8, y + 5], fill=(*c('echo', 3), 255))
    p.arrow((430, 110), (240, 128), bend=0.15, dotted=True, rgb=c('amethyst', 5))
    p.sprite('extraction_needle', 330, 196, 2)
    p.sprite('imprint_slip', 420, 196, 2)
    p.arrow((360, 196), (392, 196))
    return p.image()


def p_noticed():
    """Stage 1: an old gesture comes back. The replicant plays it; a pale flash walks the trail behind."""
    p = Page('noticed')
    d = Diorama(340, 210, 28)
    d.ground(6, 3, 1)
    d.add(mob_quads('wanderer', 1.5, 0, 0.15, 225))
    d.add(mob_quads('moment_replicant', -0.15, 0, 0.05, 225))
    d.add(mob_quads('wanderer', -1.9, 0, 0.35, 210), light='entity', tint=(*PALE, 150))
    p.center(d.render(0, 0.05, 0), 175, 128)
    for k in range(6):
        x, y = 48 + k * 28, 208 - (k % 2) * 7
        p.d.ellipse([x, y, x + 10, y + 6], fill=(*PALE, 210), outline=(*OUTLINE, 255))
    p.spark(118, 58, 26, PALE, 8)
    # Four gestures, top to bottom: a strike, a block set down, a hand on a door, a fall.
    for k, y in enumerate((48, 96, 144, 192)):
        p.plate(404, y, 474, y + 36)
    p.spark(439, 66, 12, c('ember', 4), 6)
    p.d.rectangle([427, 104, 451, 124], fill=(*c('wood', 4), 255), outline=(*OUTLINE, 255))
    p.d.rectangle([423, 152, 433, 172], fill=(*c('leather', 4), 255), outline=(*OUTLINE, 255))
    p.d.rectangle([445, 152, 455, 172], fill=(*c('wood', 5), 255), outline=(*OUTLINE, 255))
    p.arrow((439, 200), (439, 220), width=2, head=6)
    return p.image()


def p_traces():
    """Stage 2: a raised lens names a place; footsteps linger; the catalog keeps only what was read."""
    p = Page('traces')
    d = Diorama(220, 190, 30)
    d.ground(4, 3, 1)
    for i, j in ((1, 1), (2, 1), (2, 0)):
        d.add(overlay(i - 2, j - 1.5, 0, PALE, 150), light='flat')
    d.add(mob_quads('wanderer', 0.3, 0, 0.4, 200))
    p.center(d.render(0, -0.05, 0), 130, 145)
    p.sprite('chronicle_lens', 130, 42, 2)
    p.arrow((130, 64), (130, 88), dotted=True, width=2, head=6)
    p.plate(250, 36, 478, 78)
    for k, rgb in enumerate((c('ember', 4), c('bone', 3), c('brass', 4))):
        x = 266 + k * 70
        p.d.rectangle([x, 50, x + 52, 64], fill=(*rgb, 255), outline=(*OUTLINE, 255))
    p.sprite('catalog_fragment', 292, 155, 2)
    p.plate(350, 100, 478, 214, 'navy')
    for k in range(4):
        y = 118 + k * 22
        p.d.rectangle([364, y, 430, y + 8], fill=(*c('bone', 4), 255))
        p.check(456, y + 4, 6)
    return p.image()


def p_offer():
    """Stage 3: one residue offers itself. A silhouette walks true, or aside to a replicant and a pale lie."""
    p = Page('offer')
    d = Diorama(300, 180, 26)
    d.ground(6, 3, 1)
    d.add(mob_quads('residue', 1.5, 0.35, -0.3, 210), light='entity', tint=(*TEMPER['death'], 255))
    d.add(mob_quads('residue', -1.7, 0.35, 0.55, 160), light='entity', tint=(*PALE, 255))
    d.add(mob_quads('moment_replicant', -1.9, 0, 1.05, 150, 0.85))
    d.add(echo_quads(0.1, 0.55, 250), light='entity', tint=ECHO_TINT)
    d.add(mob_quads('wanderer', 0.7, 0, -0.85, 245), light='entity', tint=(*PALE, 145))
    p.center(d.render(0, 0.15, 0), 168, 108)
    p.arrow((230, 52), (300, 36), rgb=TEMPER['death'], width=2, head=6)
    p.arrow((110, 52), (48, 36), rgb=PALE, width=2, head=6, dotted=True)
    # Four ways the offer closes: mute, needle, vault, walk away.
    d = Diorama(70, 70, 48)
    d.add(block_quads('mnemolith:block/mute_stone', -0.5, 0, -0.5))
    p.center(d.render(0, 0.45, 0), 390, 70)
    p.sprite('extraction_needle', 460, 70, 2)
    d = Diorama(70, 70, 40)
    d.add(block_quads('mnemolith:block/archive_vault', -0.5, 0, -0.5))
    p.center(d.render(0, 0.4, 0), 390, 175)
    p.arrow((430, 175), (478, 175), dotted=True, width=2, head=6)
    return p.image()


def p_rewrite():
    """Stage 4: a scar fragment changes an imprint's tag. A pale lie is marked as something that cannot be kept."""
    p = Page('rewrite')
    d = Diorama(230, 200, 32)
    d.ground(3, 3, 1, bands=lambda i, j: BAND['fracture'] if i == 1 and j == 1 else None)
    d.add(mob_quads('residue', 0.15, 0.35, 0.1, 200), light='entity', tint=(*TEMPER['silence'], 255))
    p.center(d.render(0, 0.1, 0), 175, 140)
    p.sprite('scar_fragment', 70, 78, 3)
    p.arrow((108, 90), (145, 115), width=2, head=6)
    p.spark(175, 70, 28, c('amethyst', 5), 8)
    p.tag('death', 430, 52)
    p.arrow((430, 74), (430, 104), width=2, head=6)
    p.tag('silence', 430, 132)
    p.bar(360, 188, 470, 202, [(0.55, TEMPER['death']), (0.45, BAND['fracture'])], 0.72)
    d = Diorama(80, 80, 36)
    d.add(mob_quads('residue', 0, 0.2, 0, 180, 1.2), light='entity', tint=(*PALE, 255))
    p.center(d.render(0, 0.3, 0), 70, 190)
    p.cross(70, 190, 16)
    return p.image()


def p_world():
    """Cutaway: an archival vein and a mute pocket wall in the stone under the turf."""
    p = Page('world')
    d = Diorama(360, 236, 24)
    vein = {(1, 2, 2), (2, 2, 2), (2, 1, 2), (3, 1, 2), (3, 2, 2), (5, 2, 1), (5, 3, 0)}
    mute = {(4, 3, 2), (5, 3, 2), (5, 3, 1)}
    d.ground(6, 3, 4, skip=vein | mute)
    for (i, L, j) in vein:
        d.add(block_quads('mnemolith:block/archival_stratum', i - 3, -1 - L, j - 1.5))
    for (i, L, j) in mute:
        d.add(block_quads('mnemolith:block/mute_stone', i - 3, -1 - L, j - 1.5))
    p.center(d.render(0, -1.8, 0), 196, 128)
    p.sprite('archival_tablet', 440, 80, 2)
    p.sprite('catalog_fragment', 440, 180, 2)
    return p.image()


def p_places():
    """A small hall of mute brick: an open doorway, a shrine at the back; the compass that finds such places."""
    p = Page('places')
    d = Diorama(330, 230, 26)
    d.ground(5, 4, 1)
    bricks = 'mnemolith:block/mute_stone_bricks'
    # A cutaway: the back and right walls stand, the front wall is cut down to a low sill with an open doorway.
    for i in range(5):
        for y in range(3):
            d.add(block_quads(bricks, i - 2.5, y, -2.0))         # back wall
    for j in range(-1, 2):
        for y in range(3):
            d.add(block_quads(bricks, 1.5, y, j))                # right wall
    for i in (-2.5, -1.5, 0.5):
        d.add(block_quads(bricks, i, 0, 1.0))                    # low front sill, gap at the doorway
    d.add(block_quads('mnemolith:block/archive_shrine', -0.5, 0, -1.0))
    d.add(block_quads('mnemolith:block/archival_stratum_bricks', -2.5, 0, -1.0))
    p.center(d.render(0, 0.6, 0), 190, 132)
    p.arrow((86, 214), (150, 176), dotted=True, width=2, head=6)
    p.spark(214, 92, 18, c('brass', 5), 6)
    p.sprite('memory_compass', 430, 82, 2, src=_item_tex('memory_compass'))
    p.sprite('chronicle_lens', 430, 182, 2)
    return p.image()


def p_remembers():
    """The world remembers you: your pale past self lies where you fell, while you stand and watch."""
    p = Page('remembers')
    d = Diorama(330, 210, 28)
    d.ground(6, 3, 1)
    d.add(mob_quads('wanderer', 1.6, 0, 0.2, 235))
    d.add(mob_quads('wanderer', -1.2, 0, 0.1, 200), light='entity', tint=(235, 242, 255, 120), bright=1.6)
    p.center(d.render(0, 0.05, 0), 180, 128)
    p.spark(120, 62, 26, PALE, 8)
    # Four moments, top to bottom: a death, a home, a build, a won fight.
    for y in (40, 90, 140, 190):
        p.plate(404, y, 474, y + 36)
    p.cross(439, 58, 9, c('red', 3))
    p.d.rectangle([423, 102, 455, 116], fill=(*c('red', 3), 255), outline=(*OUTLINE, 255))
    p.d.rectangle([423, 112, 455, 120], fill=(*c('wood', 4), 255), outline=(*OUTLINE, 255))
    for k in range(3):
        p.d.rectangle([425 + k * 10, 162 - k * 6, 433 + k * 10, 170], fill=(*c('mute', 4), 255), outline=(*OUTLINE, 255))
    p.spark(439, 208, 12, c('brass', 5), 6)
    return p.image()


def p_players():
    p = Page('players')
    d = Diorama(360, 220, 30)
    grid = [['calm', 'saturated', 'calm', 'calm'], ['saturated', 'overloaded', 'saturated', 'calm'], ['calm', 'saturated', 'calm', None]]
    d.ground(4, 3, 2, bands=lambda i, j: band_of(i, j, grid))
    d.add(mob_quads('wanderer', -1.2, 0, 0.6, 200))
    d.add(mob_quads('wanderer', 1.3, 0, -0.5, 250), tint=(210, 225, 255))
    p.center(d.render(0, 0.1, 0), 180, 132)
    p.sprite('chronicle_lens', 420, 70, 2)
    p.sprite('catalog_fragment', 390, 170, 2)
    p.sprite('catalog_fragment', 450, 180, 2)
    return p.image()


def p_reference():
    p = Page('reference')
    # The thirteen tag variants of the imprint slip are one family: they sit on their own strip under the grid instead
    # of taking a slot each (two rows of nine slots is all the page holds above the block plates).
    variants = [n for n in items.ITEMS if n.startswith('imprint_slip_')]
    names = [n for n in items.ITEMS if not n.endswith('spawn_egg') and n not in variants]
    s = gui.slot()
    cols = 9
    assert len(names) <= 2 * cols, 'reference grid overflows: %d items' % len(names)
    for k, n in enumerate(names):
        x = 48 + (k % cols) * 52
        y = 52 + (k // cols) * 50
        p.center(s, x, y, shadow=False)
        p.sprite(n, x, y, 1, shadow=False)
    step = 32
    x0 = W // 2 - (len(variants) - 1) * step // 2
    p.plate(x0 - 22, 128, x0 + (len(variants) - 1) * step + 22, 166)
    for k, n in enumerate(variants):
        p.sprite(n, x0 + k * step, 147, 1, shadow=False)
    plates = ('mute_stone', 'composition_reel', 'resonator_trap', 'archive_vault', 'archival_stratum',
              'scar_glass', 'scar_heart')
    for k, n in enumerate(plates):
        d = Diorama(56, 56, 28)
        d.add(block_quads('mnemolith:block/' + n, -0.5, 0, -0.5))
        p.center(d.render(0, 0.45, 0), 40 + k * 68, 214)
    return p.image()



def _item_tex(name):
    path = Path(__file__).resolve().parents[3] / 'src/main/resources/assets/mnemolith/textures/item' / (name + '.png')
    return Image.open(path).convert('RGBA')


def p_armory():
    p = Page('armory')
    names = ('hush_helmet', 'grave_chestplate', 'echo_leggings', 'scar_boots',
             'recall_blade', 'hush_spear', 'grave_maul', 'chorus_sling', 'scar_brand')
    for i, n in enumerate(names):
        x = 70 + (i % 5) * 80
        y = 70 + (i // 5) * 90
        p.sprite(n, x, y, 2, src=_item_tex(n))
    return p.image()


def p_beasts():
    p = Page('beasts')
    # Egg icons stand in for the three quieter beasts when entity dioramas are unavailable here.
    for i, n in enumerate(('ledger_mite_spawn_egg', 'kin_witness_spawn_egg', 'fracture_stalker_spawn_egg')):
        p.sprite(n, 110 + i * 120, 120, 3, src=_item_tex(n))
    return p.image()


def p_roles():
    p = Page('roles')
    d = Diorama(360, 200, 28)
    d.ground(4, 3, 2)
    d.add(mob_quads('wanderer', -0.8, 0, 0.2, 200), tint=ECHO_TINT)
    d.add(mob_quads('wanderer', 1.0, 0, -0.3, 240), tint=ECHO_TINT)
    p.center(d.render(0, 0.12, 0), 220, 130)
    p.sprite('echo_slip', 420, 70, 2)
    p.sprite('recall_blade', 430, 150, 2, src=_item_tex('recall_blade'))
    return p.image()


def p_guard():
    p = Page('guard')
    d = Diorama(380, 230, 27)
    d.ground(8, 6, 1)
    # the post radius: a ring of pale tiles around the echo's post
    for x in range(-4, 4):
        for z in range(-3, 3):
            r = math.hypot(x + 0.5, z + 0.5)
            if 2.4 <= r <= 3.3:
                d.add(overlay(x, z, 0, LINE, 120), light='flat')
    d.add(echo_quads(0.1, 0.1, 225, {'right_arm': (-2.1, 0, 0.2)}), light='entity', tint=ECHO_TINT)
    d.add(mob_quads('archivist', 1.9, 0, 0.9, 45))
    d.add(mob_quads('wanderer', -2.0, 0, -1.2, 225))
    p.center(d.render(0, 0.15, 0), 210, 132)
    p.sprite('echo_recording', 440, 60, 2)
    for k, t in enumerate(('death', 'explosion', 'silence')):
        p.tag(t, 440, 130 + k * 42)
    return p.image()


FLICKER_TINT = (220, 224, 246, 150)     # FlickerRenderer's pale base, translucent


def p_hollows():
    """Memory Hollows: faded turf over hollowstone, a broken remnant showing recollite, a flicker walking past."""
    p = Page('hollows')
    for alias, tex in (('h_top', 'hollow_turf_top'), ('h_side', 'hollow_turf_side'), ('h_stone', 'hollowstone'),
                       ('h_brick', 'hollowstone_bricks'), ('h_ore', 'recollite_ore'), ('h_crystal', 'recollite_block')):
        R._tex_cache['guide:' + alias] = R.texture('mnemolith:block/' + tex)
    d = Diorama(370, 230, 27)
    d.ground(7, 5, 2, top='h_top', side='h_side', under='h_stone', skip={(5, 4), (6, 4), (6, 3)})
    # the sunken corner: one step down, faded turf on the floor
    for i, j in ((5, 4), (6, 4), (6, 3)):
        d.add(cube('h_top', 'h_side', i - 3.5, -2, j - 2.5))
    # remnant: a broken brick pillar with an ore block showing, and a lone stump beside it
    for k, t in enumerate(('h_brick', 'h_ore', 'h_brick', 'h_brick')):
        d.add(cube(t, t, -3.5, k, -1.5))
    d.add(cube('h_brick', 'h_brick', -3.5, 0, -0.5))
    d.add(cube('h_crystal', 'h_crystal', -3.5, 1, -0.5, h=0.5))
    d.add(cube('h_stone', 'h_stone', -1.5, 0, 1.5, h=0.5))
    d.add(mob_quads('echo', 0.5, 0, 0.0, 210), light='entity', tint=FLICKER_TINT)
    d.add(mob_quads('echo', 1.5, 0, -2.0, 160), light='entity', tint=(*FLICKER_TINT[:3], 90))
    p.center(d.render(0, 0.3, 0), 200, 132)
    p.sprite('recollite_shard', 430, 62, 2, src=_hollows_tex('item', 'recollite_shard'))
    p.sprite('forget_me_not', 430, 134, 2, src=_hollows_tex('block', 'forget_me_not'))
    plate = Diorama(70, 70, 30)
    plate.add(block_quads('mnemolith:block/recollite_ore', -0.5, 0, -0.5))
    p.center(plate.render(0, 0.45, 0), 430, 206)
    return p.image()


def _hollows_tex(kind, name):
    return Image.fromarray(R.texture('mnemolith:%s/%s' % (kind, name)))


BUILDERS = {n: globals()['p_' + n] for n in PAGES}


def build(name):
    return BUILDERS[name]()
