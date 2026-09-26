"""A small software renderer for Minecraft JSON models and entity boxes (previews and guide illustrations only).
z-buffered triangles, perspective-correct UVs, nearest texture sampling, alpha test plus sorted translucency,
vanilla-like face shading. Not a game renderer: good enough to judge silhouettes, materials and display transforms."""
import json
import math
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
MOD_ASSETS = os.path.join(REPO, 'src', 'main', 'resources', 'assets')
VANILLA_ASSETS = os.environ.get('MNART_VANILLA', '')  # optional extracted client jar /assets, previews only


def _asset_path(ns_id, kind, ext):
    ns, path = ns_id.split(':', 1) if ':' in ns_id else ('minecraft', ns_id)
    for root in (MOD_ASSETS, VANILLA_ASSETS):
        if not root:
            continue
        p = os.path.join(root, ns, kind, path + ext)
        if os.path.exists(p):
            return p
    return None


_tex_cache = {}

# Minimal stand-ins for the vanilla parents our models use, so guide renders never need the client jar.
# Display transforms are only used by previews; those load the real vanilla files when MNART_VANILLA is set.
_FACES = {f: {'texture': '#' + f, 'cullface': f} for f in ('down', 'up', 'north', 'south', 'west', 'east')}
BUILTIN_MODELS = {
    'minecraft:block/block': {},
    'minecraft:block/cube': {'parent': 'block/block', 'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': _FACES}]},
    'minecraft:block/cube_all': {'parent': 'block/cube', 'textures': dict({'particle': '#all'}, **{f: '#all' for f in _FACES})},
    'minecraft:item/generated': {'parent': 'builtin/generated'},
    'minecraft:item/handheld': {'parent': 'item/generated'},
}


def texture(ns_id):
    if ns_id not in _tex_cache:
        p = _asset_path(ns_id, 'textures', '.png')
        if p is None:
            img = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
        else:
            img = Image.open(p).convert('RGBA')
            if img.height > img.width:  # animated strip: first frame
                img = img.crop((0, 0, img.width, img.width))
        _tex_cache[ns_id] = np.array(img)
    return _tex_cache[ns_id]


def load_model(ns_id, overrides=None):
    """Merges the parent chain. Returns dict with textures, elements, display, gui_light, generated flag."""
    chain = []
    cur = ns_id
    while cur:
        if cur.startswith('builtin/') or cur == 'minecraft:builtin/generated':
            chain.append({'__generated__': True})
            break
        p = _asset_path(cur, 'models', '.json')
        if p is None:
            if cur not in BUILTIN_MODELS:
                raise FileNotFoundError(cur)
            data = BUILTIN_MODELS[cur]
        else:
            with open(p) as fh:
                data = json.load(fh)
        chain.append(data)
        cur = data.get('parent')
        if cur and ':' not in cur:
            cur = 'minecraft:' + cur
    out = {'textures': {}, 'elements': None, 'display': {}, 'gui_light': 'side', 'generated': False}
    for data in reversed(chain):
        if data.get('__generated__'):
            out['generated'] = True
            continue
        out['textures'].update(data.get('textures', {}))
        if 'elements' in data:
            out['elements'] = data['elements']
        for k, v in data.get('display', {}).items():
            out['display'][k] = v
        if 'gui_light' in data:
            out['gui_light'] = data['gui_light']
    if overrides:
        out['textures'].update(overrides)
    return out


def resolve_tex(model, ref):
    seen = 0
    while ref.startswith('#') and seen < 10:
        ref = model['textures'].get(ref[1:], 'missingno')
        seen += 1
    if ':' not in ref:
        ref = 'minecraft:' + ref
    return ref


# ------------------------------------------------------------------ math


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])


def trans(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def scale(x, y=None, z=None):
    y = x if y is None else y
    z = x if z is None else z
    return np.diag([x, y, z, 1.0])


def display_matrix(t):
    """ItemTransform: translate(t/16) * rotateXYZ(deg) * scale."""
    if not t:
        return np.eye(4)
    tr = t.get('translation', [0, 0, 0])
    r = [math.radians(v) for v in t.get('rotation', [0, 0, 0])]
    s = t.get('scale', [1, 1, 1])
    return trans(tr[0] / 16, tr[1] / 16, tr[2] / 16) @ rot_x(r[0]) @ rot_y(r[1]) @ rot_z(r[2]) @ scale(*s)


# ------------------------------------------------------------------ geometry

# Corners TL, TR, BR, BL of each face as seen from outside (block convention).
def face_corners(f, a, b):
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[f]


NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def default_uv(f, a, b):
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {
        'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0], 'south': [x0, 16 - y1, x1, 16 - y0],
        'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0], 'west': [z0, 16 - y1, z1, 16 - y0],
        'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0],
    }[f]


class Quad:
    __slots__ = ('pts', 'uvs', 'tex', 'normal', 'tint', 'shade', 'emissive')

    def __init__(self, pts, uvs, tex, normal, tint=None, shade=True, emissive=False):
        self.pts, self.uvs, self.tex, self.normal, self.tint, self.shade, self.emissive = pts, uvs, tex, normal, tint, shade, emissive


def element_rotation_matrix(rot):
    if not rot:
        return np.eye(4)
    o = [v / 16 for v in rot.get('origin', [8, 8, 8])]
    a = math.radians(rot.get('angle', 0))
    ax = rot.get('axis', 'y')
    r = {'x': rot_x, 'y': rot_y, 'z': rot_z}[ax](a)
    if rot.get('rescale'):
        k = 1 / math.cos(a) if abs(math.cos(a)) > 1e-6 else 1
        sc = {'x': scale(1, k, k), 'y': scale(k, 1, k), 'z': scale(k, k, 1)}[ax]
        r = r @ sc
    return trans(*o) @ r @ trans(-o[0], -o[1], -o[2])


def model_quads(model, tint=None):
    """Quads in block units (0..1 cube, centred later by the caller)."""
    quads = []
    if model['generated']:
        return generated_quads(model)
    for el in model['elements'] or []:
        a = [v / 16 for v in el['from']]
        b = [v / 16 for v in el['to']]
        rm = element_rotation_matrix(el.get('rotation'))
        for f, fd in el['faces'].items():
            corners = face_corners(f, a, b)
            pts = [tuple((rm @ np.array([*p, 1.0]))[:3]) for p in corners]
            n = np.array(NORMALS[f], float)
            n = rm[:3, :3] @ n
            uv = fd.get('uv') or default_uv(f, el['from'], el['to'])
            u0, v0, u1, v1 = uv
            uvc = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            k = (fd.get('rotation', 0) // 90) % 4
            uvc = uvc[-k:] + uvc[:-k] if k else uvc
            quads.append(Quad(pts, [(u / 16, v / 16) for u, v in uvc], resolve_tex(model, fd['texture']), n,
                              tint if fd.get('tintindex') is not None else None, el.get('shade', True)))
    return quads


def generated_quads(model):
    tex_id = resolve_tex(model, '#layer0')
    t = texture(tex_id)
    h, w = t.shape[:2]
    quads = []
    z0, z1 = 7.5 / 16, 8.5 / 16
    quads.append(Quad([(0, 1, z1), (1, 1, z1), (1, 0, z1), (0, 0, z1)], [(0, 0), (1, 0), (1, 1), (0, 1)], tex_id, np.array([0, 0, 1.0])))
    quads.append(Quad([(1, 1, z0), (0, 1, z0), (0, 0, z0), (1, 0, z0)], [(1, 0), (0, 0), (0, 1), (1, 1)], tex_id, np.array([0, 0, -1.0])))
    op = t[:, :, 3] > 0
    for y in range(h):
        for x in range(w):
            if not op[y, x]:
                continue
            X0, X1 = x / w, (x + 1) / w
            Y1, Y0 = 1 - y / h, 1 - (y + 1) / h
            uv = [((x + 0.5) / w, (y + 0.5) / h)] * 4
            if y == 0 or not op[y - 1, x]:
                quads.append(Quad([(X0, Y1, z0), (X1, Y1, z0), (X1, Y1, z1), (X0, Y1, z1)], uv, tex_id, np.array([0, 1.0, 0])))
            if y == h - 1 or not op[y + 1, x]:
                quads.append(Quad([(X0, Y0, z1), (X1, Y0, z1), (X1, Y0, z0), (X0, Y0, z0)], uv, tex_id, np.array([0, -1.0, 0])))
            if x == 0 or not op[y, x - 1]:
                quads.append(Quad([(X0, Y1, z0), (X0, Y1, z1), (X0, Y0, z1), (X0, Y0, z0)], uv, tex_id, np.array([-1.0, 0, 0])))
            if x == w - 1 or not op[y, x + 1]:
                quads.append(Quad([(X1, Y1, z1), (X1, Y1, z0), (X1, Y0, z0), (X1, Y0, z1)], uv, tex_id, np.array([1.0, 0, 0])))
    return quads


def entity_box_quads(u, v, x, y, z, w, h, d, tex_id, tex_w=64, tex_h=64, inflate=0.0):
    """One ModelPart cube in model units (y down), converted to y-up block-style quads (units of 1/16)."""
    x0, x1 = x - inflate, x + w + inflate
    y0, y1 = y - inflate, y + h + inflate
    z0, z1 = z - inflate, z + d + inflate
    # to world-like: X = -x, Y = -y
    a = (-x1 / 16, -y1 / 16, z0 / 16)
    b = (-x0 / 16, -y0 / 16, z1 / 16)
    reg = {
        'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h),
    }
    quads = []
    for f, (ru, rv, rw, rh) in reg.items():
        u0, v0, u1, v1 = ru / tex_w, rv / tex_h, (ru + rw) / tex_w, (rv + rh) / tex_h
        uvc = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        if f in ('up', 'down'):
            uvc = uvc[2:] + uvc[:2]
        quads.append(Quad(face_corners(f, a, b), uvc, tex_id, np.array(NORMALS[f], float)))
    return quads


# ------------------------------------------------------------------ scene


class Scene:
    def __init__(self, w, h, ss=3, bg=(0, 0, 0, 0)):
        self.w, self.h, self.ss = w, h, ss
        self.W, self.H = w * ss, h * ss
        self.bg = bg
        self.items = []  # (quads, matrix, light, tint)
        self.view = np.eye(4)
        self.ortho = True
        self.fov = 70.0
        self.ortho_scale = 1.0
        self.sun = np.array([0.35, 1.0, 0.55])

    def add(self, quads, matrix, light='block', tint=None, brightness=1.0):
        self.items.append((quads, matrix, light, tint, brightness))

    def add_model(self, ns_id, matrix, light=None, context=None, tint=None, brightness=1.0, overrides=None):
        m = load_model(ns_id, overrides)
        dm = np.eye(4)
        if context:
            dm = display_matrix(m['display'].get(context))
        centre = trans(-0.5, -0.5, -0.5)
        if light is None:
            light = 'flat' if (context == 'gui' and m['gui_light'] == 'front') else ('gui' if context == 'gui' else 'block')
        self.add(model_quads(m, tint), matrix @ dm @ centre, light, tint, brightness)
        return m

    def project(self, p):
        v = self.view @ np.array([p[0], p[1], p[2], 1.0])
        if self.ortho:
            sx = self.W / 2 + v[0] * self.ortho_scale * self.ss
            sy = self.H / 2 - v[1] * self.ortho_scale * self.ss
            return sx, sy, -v[2], 1.0
        zc = -v[2]
        if zc < 1e-3:
            zc = 1e-3
        f = (self.H / 2) / math.tan(math.radians(self.fov) / 2)
        return self.W / 2 + v[0] * f / zc, self.H / 2 - v[1] * f / zc, zc, 1.0 / zc

    def render(self):
        W, H = self.W, self.H
        col = np.zeros((H, W, 4), float)
        col[:, :] = self.bg
        zb = np.full((H, W), np.inf)
        translucent = []
        for quads, mat, light, tint, bright in self.items:
            nm = mat[:3, :3]
            for q in quads:
                pts = [mat @ np.array([*p, 1.0]) for p in q.pts]
                wn = nm @ q.normal
                ln = np.linalg.norm(wn)
                wn = wn / ln if ln > 0 else wn
                shade = self._shade(wn, light, q.shade) * bright
                vn = (self.view[:3, :3] @ wn)
                proj = [self.project(p[:3]) for p in pts]
                tex = texture(q.tex)
                for tri in ((0, 1, 2), (0, 2, 3)):
                    self._raster([proj[i] for i in tri], [q.uvs[i] for i in tri], tex, shade, tint or q.tint, col, zb, translucent)
        for depth, xs, ys, rgba in sorted(translucent, key=lambda t: -t[0]):
            a = rgba[:, 3:4] / 255.0            # straight-alpha "over" onto whatever is below (maybe transparent)
            ca = col[ys, xs, 3:4] / 255.0
            oa = a + ca * (1 - a)
            col[ys, xs, :3] = (rgba[:, :3] * a + col[ys, xs, :3] * ca * (1 - a)) / np.maximum(oa, 1e-6)
            col[ys, xs, 3:4] = oa * 255.0
        img = Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), 'RGBA')
        if self.ss > 1:
            img = img.resize((self.w, self.h), Image.LANCZOS)
        return img

    def _shade(self, n, light, enabled):
        if not enabled or light == 'flat':
            return 1.0
        x, y, z = n
        if light == 'block':
            return min(1.0, x * x * 0.6 + y * y * ((3 + y) / 4) + z * z * 0.8)
        if light == 'gui':   # Lighting.setupFor3DItems approximation, in view space
            vn = self.view[:3, :3] @ n
            l0 = np.array([0.2, 1.0, -0.7]); l0 /= np.linalg.norm(l0)
            l1 = np.array([-0.2, 1.0, 0.7]); l1 /= np.linalg.norm(l1)
            vn = np.array([vn[0], vn[1], -vn[2]])
            return min(1.0, 0.4 + 0.6 * (max(0, vn @ l0) + max(0, vn @ l1)))
        if light == 'entity':
            l0 = np.array([0.2, 1.0, -0.7]); l0 /= np.linalg.norm(l0)
            l1 = np.array([-0.2, 1.0, 0.7]); l1 /= np.linalg.norm(l1)
            return min(1.0, 0.4 + 0.6 * (max(0, n @ l0) + max(0, n @ l1)))
        return 1.0

    def _raster(self, P, UV, tex, shade, tint, col, zb, translucent):
        (x0, y0, z0, w0), (x1, y1, z1, w1), (x2, y2, z2, w2) = P
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9:
            return
        minx, maxx = int(max(0, math.floor(min(x0, x1, x2)))), int(min(self.W - 1, math.ceil(max(x0, x1, x2))))
        miny, maxy = int(max(0, math.floor(min(y0, y1, y2)))), int(min(self.H - 1, math.ceil(max(y0, y1, y2))))
        if minx > maxx or miny > maxy:
            return
        ys, xs = np.mgrid[miny:maxy + 1, minx:maxx + 1]
        px, py = xs + 0.5, ys + 0.5
        b0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area
        b1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area
        b2 = 1 - b0 - b1
        inside = (b0 >= -1e-6) & (b1 >= -1e-6) & (b2 >= -1e-6)
        if not inside.any():
            return
        b0, b1, b2, xs, ys = b0[inside], b1[inside], b2[inside], xs[inside], ys[inside]
        iw = b0 * w0 + b1 * w1 + b2 * w2
        u = (b0 * UV[0][0] * w0 + b1 * UV[1][0] * w1 + b2 * UV[2][0] * w2) / iw
        v = (b0 * UV[0][1] * w0 + b1 * UV[1][1] * w1 + b2 * UV[2][1] * w2) / iw
        z = b0 * z0 + b1 * z1 + b2 * z2 if w0 == 1.0 else 1.0 / iw
        th, tw = tex.shape[:2]
        tx = np.clip((u * tw).astype(int), 0, tw - 1)
        ty = np.clip((v * th).astype(int), 0, th - 1)
        rgba = tex[ty, tx].astype(float)
        a = rgba[:, 3]
        rgb = rgba[:, :3] * shade
        if tint is not None:
            rgb = rgb * np.array(tint[:3]) / 255.0
            if len(tint) > 3:
                a = a * tint[3] / 255.0
        opaque = a >= 250
        closer = z < zb[ys, xs]
        sel = opaque & closer
        if sel.any():
            zb[ys[sel], xs[sel]] = z[sel]
            col[ys[sel], xs[sel], :3] = rgb[sel]
            col[ys[sel], xs[sel], 3] = 255
        tsel = (a > 8) & ~opaque & closer
        if tsel.any():
            translucent.append((float(z[tsel].mean()), xs[tsel], ys[tsel], np.column_stack([rgb[tsel], a[tsel]])))


def iso_view(yaw=225, pitch=30):
    """GUI-style camera: the model's own display.gui already rotates it, so views usually stay identity."""
    return rot_x(math.radians(pitch)) @ rot_y(math.radians(yaw))


def look_at(eye, target, up=(0, 1, 0)):
    eye, target, up = np.array(eye, float), np.array(target, float), np.array(up, float)
    f = target - eye
    f /= np.linalg.norm(f)
    s = np.cross(f, up); s /= np.linalg.norm(s)
    u = np.cross(s, f)
    m = np.eye(4)
    m[0, :3], m[1, :3], m[2, :3] = s, u, -f
    m[:3, 3] = -m[:3, :3] @ eye
    return m
