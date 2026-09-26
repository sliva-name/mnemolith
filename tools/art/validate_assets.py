#!/usr/bin/env python3
"""Offline resource check for Mnemolith (no client needed).

Resolves every reference the game would follow and fails on anything that would show up as a missing model or the
magenta-black texture: blockstates -> models, item definitions -> models, model parents and #texture variables,
particle definitions -> sprites, texture paths hard-coded in Java, guide pages. Also enforces the art rules from
docs/asset-pipeline.md (resolution, element bounds, UV bounds).

    python tools/art/validate_assets.py [--vanilla /path/to/extracted/client/assets]

Without --vanilla (or MNART_VANILLA), minecraft: references are only checked against a short list of known files."""
import argparse
import glob
import json
import os
import re
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(REPO, 'src', 'main', 'resources', 'assets')
JAVA = os.path.join(REPO, 'src', 'main', 'java')
MOD = 'mnemolith'
KNOWN_VANILLA = {
    'models': {'block/block', 'block/cube', 'block/cube_all', 'item/generated', 'item/handheld', 'item/template_spawn_egg'},
    'textures': {'block/light_blue_stained_glass'},
}
ALLOWED_ANGLES = {-45.0, -22.5, 0.0, 22.5, 45.0}

errors, warnings = [], []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def split(ns_id):
    return ns_id.split(':', 1) if ':' in ns_id else ('minecraft', ns_id)


class Resolver:
    def __init__(self, vanilla):
        self.vanilla = vanilla
        self.used_textures = set()

    def path(self, ns_id, kind, ext):
        ns, p = split(ns_id)
        for root in (ASSETS, self.vanilla):
            if root:
                f = os.path.join(root, ns, kind, p + ext)
                if os.path.exists(f):
                    return f
        return None

    def exists(self, ns_id, kind, ext):
        ns, p = split(ns_id)
        if self.path(ns_id, kind, ext):
            return True
        return ns == 'minecraft' and not self.vanilla and p in KNOWN_VANILLA.get(kind, set())

    def model_chain(self, ns_id, where):
        chain, cur, seen = [], ns_id, set()
        while cur:
            if cur in seen:
                err('%s: parent loop at %s' % (where, cur))
                break
            seen.add(cur)
            if cur.startswith('builtin/') or cur == 'minecraft:builtin/generated':
                break
            f = self.path(cur, 'models', '.json')
            if f is None:
                if not self.exists(cur, 'models', '.json'):
                    err('%s: missing model %s' % (where, cur))
                break
            with open(f) as fh:
                data = json.load(fh)
            chain.append((cur, data))
            cur = data.get('parent')
            if cur and ':' not in cur and not cur.startswith('builtin/'):
                cur = 'minecraft:' + cur
        return chain

    def check_model(self, ns_id, where):
        chain = self.model_chain(ns_id, where)
        if not chain:
            return
        textures, elements = {}, None
        for _, data in reversed(chain):
            textures.update(data.get('textures', {}))
            if 'elements' in data:
                elements = data['elements']

        def resolve(ref, ctx):
            hops = 0
            while ref.startswith('#'):
                key = ref[1:]
                if key not in textures:
                    err('%s: %s uses undefined texture variable %s' % (where, ctx, ref))
                    return
                ref = textures[key]
                hops += 1
                if hops > 10:
                    err('%s: texture variable loop %s' % (where, ref))
                    return
            if ':' not in ref:
                ref = 'minecraft:' + ref
            self.used_textures.add(ref)
            if not self.exists(ref, 'textures', '.png'):
                err('%s: missing texture %s (%s)' % (where, ref, ctx))
        own = chain[0][1]
        for k, v in own.get('textures', {}).items():
            resolve(v, 'textures.' + k)
        if split(ns_id)[0] != MOD:
            return
        for i, el in enumerate(own.get('elements', [])):
            ctx = 'element %d' % i
            for v in el['from'] + el['to']:
                if not -16 <= v <= 32:
                    err('%s: %s coordinate %s outside -16..32' % (where, ctx, v))
            rot = el.get('rotation')
            if rot and float(rot.get('angle', 0)) not in ALLOWED_ANGLES:
                warn('%s: %s rotation %s is not a classic 22.5 step' % (where, ctx, rot.get('angle')))
            for f, fd in el.get('faces', {}).items():
                resolve(fd['texture'], '%s.%s' % (ctx, f))
                uv = fd.get('uv')
                if uv and not all(0 <= u <= 16 for u in uv):
                    err('%s: %s.%s uv %s outside 0..16' % (where, ctx, f, uv))
                cf = fd.get('cullface')
                if cf and cf not in ('down', 'up', 'north', 'south', 'west', 'east'):
                    err('%s: %s.%s bad cullface %s' % (where, ctx, f, cf))
        if elements is None and not any(n.startswith('minecraft:item/generated') or n == 'minecraft:item/generated'
                                        for n, _ in chain) and 'parent' not in own:
            warn('%s: model has no elements' % where)


def item_models(node, out):
    if isinstance(node, dict):
        t = node.get('type', '')
        if t in ('minecraft:model', 'model') and 'model' in node:
            out.append(node['model'])
        if t in ('minecraft:special', 'special') and 'base' in node:
            out.append(node['base'])
        for v in node.values():
            item_models(v, out)
    elif isinstance(node, list):
        for v in node:
            item_models(v, out)


def java_ids(cls):
    src = open(os.path.join(JAVA, 'com', 'mnemolith', 'content', cls)).read()
    return set(re.findall(r'"([a-z0-9_]+)"', src)) - {MOD}


def size(p):
    with Image.open(p) as im:
        return im.size


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--vanilla', default=os.environ.get('MNART_VANILLA', ''))
    a = ap.parse_args()
    r = Resolver(a.vanilla)
    base = os.path.join(ASSETS, MOD)
    counts = {}

    for f in sorted(glob.glob(os.path.join(base, 'blockstates', '*.json'))):
        name = os.path.basename(f)[:-5]
        data = json.load(open(f))
        refs = []
        for v in data.get('variants', {}).values():
            refs += [x['model'] for x in (v if isinstance(v, list) else [v])]
        for part in data.get('multipart', []):
            ap_ = part['apply']
            refs += [x['model'] for x in (ap_ if isinstance(ap_, list) else [ap_])]
        for m in refs:
            r.check_model(m, 'blockstate %s' % name)
        counts['blockstates'] = counts.get('blockstates', 0) + 1

    for f in sorted(glob.glob(os.path.join(base, 'items', '*.json'))):
        name = os.path.basename(f)[:-5]
        refs = []
        item_models(json.load(open(f)), refs)
        if not refs:
            err('item %s: definition has no model' % name)
        for m in refs:
            r.check_model(m, 'item %s' % name)
        counts['items'] = counts.get('items', 0) + 1

    for f in sorted(glob.glob(os.path.join(base, 'models', '**', '*.json'), recursive=True)):
        rel = os.path.relpath(f, os.path.join(base, 'models'))[:-5]
        r.check_model('%s:%s' % (MOD, rel), 'model %s' % rel)
        counts['models'] = counts.get('models', 0) + 1

    for f in sorted(glob.glob(os.path.join(base, 'particles', '*.json'))):
        for t in json.load(open(f)).get('textures', []):
            ns, p = split(t)
            ref = '%s:particle/%s' % (ns, p)
            r.used_textures.add(ref)
            if not r.exists(ref, 'textures', '.png'):
                err('particle %s: missing sprite %s' % (os.path.basename(f), ref))
        counts['particles'] = counts.get('particles', 0) + 1

    # registry coverage
    for i in sorted(java_ids('ModItems.java')):
        if not os.path.exists(os.path.join(base, 'items', i + '.json')):
            err('registered item %s has no items/%s.json' % (i, i))
    for b in sorted(java_ids('ModBlocks.java')):
        if not os.path.exists(os.path.join(base, 'blockstates', b + '.json')):
            err('registered block %s has no blockstate' % b)

    # texture paths hard-coded in Java, and the guide pages
    java_src = ''.join(open(p).read() for p in glob.glob(os.path.join(JAVA, '**', '*.java'), recursive=True))
    for t in sorted(set(re.findall(r'"(textures/[a-z0-9_/]+\.png)"', java_src))):
        p = os.path.join(base, t)
        r.used_textures.add('%s:%s' % (MOD, t[len('textures/'):-4]))
        if not os.path.exists(p):
            err('Java references missing %s' % t)
    m = re.search(r'PAGES\s*=\s*\{([^}]*)\}', open(os.path.join(JAVA, 'com', 'mnemolith', 'content', 'guide', 'GuideBook.java')).read())
    pages = re.findall(r'"([a-z_]+)"', m.group(1)) if m else []
    if not pages:
        err('could not read GuideBook.PAGES')
    for pg in pages:
        p = os.path.join(base, 'textures', 'gui', 'guide', pg + '.png')
        r.used_textures.add('%s:gui/guide/%s' % (MOD, pg))
        if not os.path.exists(p):
            err('guide page %s missing' % pg)
        elif size(p) != (512, 256):
            err('guide page %s is %sx%s, expected 512x256' % ((pg,) + size(p)))

    # resolution rules (docs/asset-pipeline.md)
    for p in sorted(glob.glob(os.path.join(base, 'textures', '**', '*.png'), recursive=True)):
        rel = os.path.relpath(p, os.path.join(base, 'textures'))[:-4]
        w, h = size(p)
        kind = rel.split('/')[0]
        ok = True
        if kind in ('item', 'particle'):
            ok = (w, h) == (32, 32) or rel.endswith('_model') and (w, h) == (64, 64)
        elif kind == 'block':
            ok = (w, h) in ((32, 32), (64, 64))
        elif kind == 'entity':
            ok = (w, h) == (128, 128)
        if not ok:
            err('texture %s is %sx%s, breaks the resolution rule' % (rel, w, h))
        if '%s:%s' % (MOD, rel) not in r.used_textures:
            warn('texture %s is not referenced by any model, particle, Java path or guide page' % rel)
        if os.path.exists(p + '.mcmeta'):
            json.load(open(p + '.mcmeta'))

    print('checked: %s; textures referenced: %d' % (', '.join('%d %s' % (v, k) for k, v in counts.items()), len(r.used_textures)))
    for w_ in warnings:
        print('WARN ', w_)
    for e in errors:
        print('ERROR', e)
    print('%d errors, %d warnings' % (len(errors), len(warnings)))
    sys.exit(1 if errors else 0)


if __name__ == '__main__':
    main()
