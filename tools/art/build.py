#!/usr/bin/env python3
"""Mnemolith art pipeline: regenerates every mod texture, block/item model and guide illustration.

    python tools/art/build.py            # write into src/main/resources/assets/mnemolith
    python tools/art/build.py --check    # rebuild into a temp dir and fail if anything differs (reproducibility)
    python tools/art/build.py --only items,blocks

Deterministic: every sprite seeds its RNG from its own name (mnart.core.rng_for), so output is byte-stable.
Needs Python 3.10+, Pillow and numpy. Nothing here touches gameplay code or data."""
import argparse
import filecmp
import json
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(REPO, 'src', 'main', 'resources', 'assets')

from mnart import blocks, entities, gui, guide, handheld, items, modelgen, particles, render3d  # noqa: E402

# Files this pipeline replaced; removed on build so nothing stale ships.
STALE = [
    'textures/block/archive_vault_side.png', 'textures/block/archive_vault_side_on.png',
    'textures/block/archive_vault_top.png', 'textures/block/scar_heart_base.png',
]


def select_item(name):
    """Flat sprite in GUI/ground/frame/shelf, element model in hands (same shape as the vanilla spyglass)."""
    return {'model': {
        'type': 'minecraft:select', 'property': 'minecraft:display_context',
        'cases': [{'when': ['gui', 'ground', 'fixed', 'on_shelf'],
                   'model': {'type': 'minecraft:model', 'model': 'mnemolith:item/' + name}}],
        'fallback': {'type': 'minecraft:model', 'model': 'mnemolith:item/%s_in_hand' % name},
    }}


class Out:
    def __init__(self, root):
        self.root = os.path.join(root, 'mnemolith')
        self.written = []

    def path(self, rel):
        p = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        self.written.append(rel)
        return p

    def png(self, rel, img):
        img.save(self.path(rel), optimize=False)

    def json(self, rel, obj):
        with open(self.path(rel), 'w') as fh:
            fh.write(modelgen.dump(obj))


def build_items(o):
    for n, fn in items.ITEMS.items():
        o.png('textures/item/%s.png' % n, fn())
    for n, fn in handheld.HANDHELD_MODELS.items():
        img, model = fn()
        o.png('textures/item/%s.png' % model['textures']['sheet'].split('/')[-1], img)
        o.json('models/item/%s_in_hand.json' % n, model)
        o.json('items/%s.json' % n, select_item(n))


def build_blocks(o):
    particles_done = set()
    for n, fn in blocks.MODELS.items():
        img, model = fn()
        o.png('textures/block/%s.png' % n, img)
        pname, pmat = blocks.PARTICLE[n]
        if pname not in particles_done:
            o.png('textures/block/%s_particle.png' % pname, blocks.particle_sprite(pname, pmat))
            particles_done.add(pname)
        model['textures']['particle'] = 'mnemolith:block/%s_particle' % pname
        o.json('models/block/%s.json' % n, model)
    for n, fn in blocks.CUBE_TEXTURES.items():
        o.png('textures/block/%s.png' % n, fn())


def build_entities(o):
    for n, fn in entities.ENTITIES.items():
        o.png('textures/entity/%s.png' % n, fn())


def build_particles(o):
    for n, fn in particles.PARTICLES.items():
        o.png('textures/particle/%s.png' % n, fn())


def build_gui(o):
    for n, fn in gui.GUI.items():
        o.png('textures/gui/%s.png' % n, fn())


def build_guide(o):
    # guide dioramas render the freshly built models/textures, so point the renderer at this output
    render3d.MOD_ASSETS = os.path.dirname(o.root)
    render3d._tex_cache.clear()
    for n in guide.PAGES:
        o.png('textures/gui/guide/%s.png' % n, guide.build(n))


STAGES = {'items': build_items, 'blocks': build_blocks, 'entities': build_entities, 'particles': build_particles,
          'gui': build_gui, 'guide': build_guide}


def run(root, only):
    o = Out(root)
    for name, fn in STAGES.items():
        if only and name not in only:
            continue
        fn(o)
        print('%-9s ok' % name)
    return o


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--only', default='')
    a = ap.parse_args()
    only = set(filter(None, a.only.split(',')))
    if a.check:
        tmp = tempfile.mkdtemp(prefix='mnart-')
        try:
            if 'guide' in only or not only:   # guide needs the models from the same run
                for rel in ('models', 'items', 'textures', 'blockstates'):
                    src = os.path.join(ASSETS, 'mnemolith', rel)
                    shutil.copytree(src, os.path.join(tmp, 'mnemolith', rel))
            o = run(tmp, only)
            diff = [r for r in o.written if not os.path.exists(os.path.join(ASSETS, 'mnemolith', r))
                    or not filecmp.cmp(os.path.join(tmp, 'mnemolith', r), os.path.join(ASSETS, 'mnemolith', r), shallow=False)]
            print('%d files checked, %d differ' % (len(o.written), len(diff)))
            for r in diff:
                print('  differs:', r)
            sys.exit(1 if diff else 0)
        finally:
            shutil.rmtree(tmp)
    o = run(ASSETS, only)
    for rel in STALE:
        p = os.path.join(ASSETS, 'mnemolith', rel)
        if os.path.exists(p):
            os.remove(p)
            print('removed stale', rel)
    print('%d files written' % len(o.written))


if __name__ == '__main__':
    main()
