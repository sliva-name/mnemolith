"""Element-model builder. Each face is painted at 2 texels per model unit (32x) and packed into one 64x64 sheet per
model, so UV = texel / 4. Identical faces (same painter key and size) share one region."""
import json
import re

from . import core
from .materials import surface

SHEET = 64
UV_PER_PX = 16.0 / SHEET
FACES = ('north', 'south', 'east', 'west', 'up', 'down')

# Display presets (vanilla block/block values).
BLOCK_DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
}


def face_size(frm, to, face):
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[face]


class Box:
    def __init__(self, frm, to, faces, rotation=None, shade=True, name=None):
        self.frm, self.to, self.faces, self.rotation, self.shade, self.name = frm, to, faces, rotation, shade, name


class FaceSpec:
    """How to paint a face: a material name, or a painter(w, h, rng) -> Canvas. key groups identical faces."""

    def __init__(self, mat=None, painter=None, key=None, kind=None, wear=0.4, bevel=1, shift=0.0, cull=None, rot=0, flip=False, density=1.0):
        self.density = density
        self.mat, self.painter, self.kind, self.wear, self.bevel, self.shift = mat, painter, kind, wear, bevel, shift
        self.key = key or (mat, kind, wear, bevel, shift, density)
        self.cull, self.rot, self.flip = cull, rot, flip


def F(mat=None, **kw):
    return FaceSpec(mat=mat, **kw)


def P(painter, key, **kw):
    return FaceSpec(painter=painter, key=key, **kw)


def _kind(s, f):
    if s.painter:
        return 'painted'
    return s.kind or ('top' if f == 'up' else 'bottom' if f == 'down' else 'side')


def _size(b, f, s):
    fw, fh = face_size(b.frm, b.to, f)
    w, h = max(1, int(round(fw * 2 * s.density))), max(1, int(round(fh * 2 * s.density)))
    if s.rot in (90, 270):
        w, h = h, w
    return w, h


class Model:
    def __init__(self, name, folder='block', seed=None):
        self.name, self.folder = name, folder
        self.boxes = []
        self.rng = core.rng_for(seed or name)

    def box(self, frm, to, spec, rotation=None, shade=True, skip=(), name=None, **per_face):
        """spec paints every face; north=..., up=... override one face; skip leaves faces out."""
        faces = {}
        for f in FACES:
            if f in skip:
                continue
            s = per_face.get(f, spec)
            if s is not None:
                faces[f] = s
        self.boxes.append(Box(list(frm), list(to), faces, rotation, shade, name))
        return self

    def _group(self, b, f, s):
        if s.painter:
            w, h = _size(b, f, s)
            return (s.key, 'painted', w, h)
        return (s.key, _kind(s, f))

    def build(self, texture_name=None, display=None, extra=None, particle=None, parent='minecraft:block/block', gui_light=None,
              ambient=True):
        texture_name = texture_name or self.name
        groups, order = {}, []   # material faces share one region per material (cropped); painted faces keep exact sizes
        for b in self.boxes:
            for f, s in b.faces.items():
                g = self._group(b, f, s)
                w, h = _size(b, f, s)
                if g not in groups:
                    groups[g] = [w, h, s]
                    order.append(g)
                else:
                    groups[g][0] = max(groups[g][0], w)
                    groups[g][1] = max(groups[g][1], h)
        regions = {}
        items = sorted(order, key=lambda g: (-groups[g][0] * groups[g][1], -groups[g][1]))
        sky = [0] * SHEET   # skyline packer: lowest fitting spot, leftmost
        for g in items:
            w, h, s = groups[g]
            best = None
            for x in range(0, SHEET - w + 1):
                y = max(sky[x:x + w])
                if y + h <= SHEET and (best is None or y < best[1]):
                    best = (x, y)
            if best is None:
                raise ValueError(f'{self.name}: faces do not fit a {SHEET}px sheet')
            x, y = best
            for i in range(x, x + w):
                sky[i] = y + h
            regions[g] = (x, y, w, h)
        img = core.Canvas(SHEET, SHEET).image()
        for g in items:
            x0, y0, w, h = regions[g]
            s = groups[g][2]
            if s.painter:
                cv = s.painter(w, h, self.rng)
            else:
                cv = surface(s.mat, w, h, self.rng, face=g[1], bevel=s.bevel, wear=s.wear, base_shift=s.shift)
            img.alpha_composite(cv.image() if hasattr(cv, 'image') else cv, (x0, y0))
        self.regions = regions
        elements = []
        for b in self.boxes:
            el = {'from': b.frm, 'to': b.to}
            if b.name:
                el['name'] = b.name
            if b.rotation:
                el['rotation'] = b.rotation
            if not b.shade:
                el['shade'] = False
            el['faces'] = {}
            for f, s in b.faces.items():
                w, h = _size(b, f, s)
                x0, y0, rw, rh = regions[self._group(b, f, s)]
                if not s.painter:   # a material face: centre the crop inside the shared region
                    x0 += (rw - w) // 2
                    y0 += (rh - h) // 2
                uv = [x0 * UV_PER_PX, y0 * UV_PER_PX, (x0 + w) * UV_PER_PX, (y0 + h) * UV_PER_PX]
                if s.flip:
                    uv = [uv[2], uv[1], uv[0], uv[3]]
                fd = {'uv': [round(v, 4) for v in uv], 'texture': '#sheet'}
                if s.rot:
                    fd['rotation'] = s.rot
                cull = s.cull if s.cull is not None else auto_cull(b, f)
                if cull:
                    fd['cullface'] = cull
                el['faces'][f] = fd
            elements.append(el)
        tex_id = f'mnemolith:{self.folder}/{texture_name}'
        model = {'parent': parent} if parent else {}
        if not ambient:
            model['ambientocclusion'] = False
        if gui_light:
            model['gui_light'] = gui_light
        model['textures'] = {'particle': particle or tex_id, 'sheet': tex_id}
        if extra:
            model['textures'].update(extra)
        model['elements'] = elements
        model['display'] = display if display is not None else BLOCK_DISPLAY
        return img, model


def auto_cull(b, f):
    if b.rotation:
        return None
    lo, hi = b.frm, b.to
    return {
        'down': 'down' if lo[1] <= 0 else None, 'up': 'up' if hi[1] >= 16 else None,
        'north': 'north' if lo[2] <= 0 else None, 'south': 'south' if hi[2] >= 16 else None,
        'west': 'west' if lo[0] <= 0 else None, 'east': 'east' if hi[0] >= 16 else None,
    }[f]


def dump(obj):
    """Stable JSON; short numeric lists stay on one line."""
    text = json.dumps(obj, indent=2)
    return re.sub(r'\[\s+([-\d.,\s]+?)\s+\]', lambda m: '[' + ', '.join(p.strip() for p in m.group(1).split(',')) + ']', text) + '\n'
