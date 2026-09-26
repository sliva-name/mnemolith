#!/usr/bin/env python3
"""Offline preview sheets for the art pass: before/after comparisons, GUI/inventory, first- and third-person hands,
and an in-world perspective scene. Renders with mnart.render3d, which approximates the game (vanilla face shading,
display transforms, no lightmap/AO), so these judge silhouettes, materials and scale, not final lighting.

    python tools/art/preview.py --before <git-rev> --out <dir> [--vanilla <extracted client assets>]

--vanilla is only used for context blocks (grass, stone, planks...) in the in-world scene; nothing from it is shipped."""
import argparse
import math
import os
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
NEW_ASSETS = os.path.join(REPO, 'src', 'main', 'resources', 'assets')

from mnart import entity_models as EM, guide, items, render3d as R, core  # noqa: E402
from mnart.palette import RAMPS  # noqa: E402

BG = (46, 48, 62, 255)
LABEL = (220, 214, 200, 255)
BLOCKS = ['archival_stratum', 'mute_stone', 'composition_reel', 'resonator_trap', 'archive_vault', 'scar_glass', 'scar_heart']
MOBS = [('archivist', None, 1.0), ('echo_strider', None, 1.0), ('moment_replicant', None, 1.0),
        ('residue', (255, 154, 92, 255), 1.0), ('scar', None, 2.0), ('echo', guide.ECHO_TINT, 1.0)]


def use_assets(root):
    R.MOD_ASSETS = root
    R._tex_cache.clear()
    guide._ready = False
    guide._prepare()


def extract_before(rev):
    tmp = tempfile.mkdtemp(prefix='mnart-before-')
    tar = subprocess.run(['git', '-C', REPO, 'archive', rev, 'src/main/resources/assets'], check=True, capture_output=True).stdout
    subprocess.run(['tar', '-x', '-C', tmp], input=tar, check=True)
    return os.path.join(tmp, 'src', 'main', 'resources', 'assets')


def text(img, xy, s, fill=LABEL):
    ImageDraw.Draw(img).text(xy, s, fill=fill)


def gui_block(name, px=120):
    s = R.Scene(px, px, ss=3)
    s.ortho_scale = px * 0.94
    try:
        s.add_model('mnemolith:block/' + name, np.eye(4), context='gui')
    except FileNotFoundError:
        return Image.new('RGBA', (px, px))
    return s.render()


def item_tile(root, name, px=96):
    p = os.path.join(root, 'mnemolith', 'textures', 'item', name + '.png')
    if not os.path.exists(p):
        return Image.new('RGBA', (px, px))
    return Image.open(p).convert('RGBA').resize((px, px), Image.NEAREST)


def mob_render(name, tint, k, w=150, h=190, yaw=205):
    s = R.Scene(w, h, ss=2)
    s.ortho_scale = 70 / max(1.0, k * 0.75)
    s.view = R.rot_x(math.radians(12)) @ R.rot_y(math.radians(yaw))
    q = guide.mob_quads(name, 0, 0, 0, 0, k)
    s.add(q, R.trans(0, -1.05 * max(1.0, k * 0.75), 0), light='entity', tint=tint)
    return s.render()


# ------------------------------------------------------------------ sheets

def sheet_items(before, out):
    names = list(items.ITEMS)
    cols = 5
    tw, th = 230, 130
    rows = (len(names) + cols - 1) // cols
    img = Image.new('RGBA', (cols * tw, rows * th + 24), BG)
    text(img, (8, 6), 'items: before (left, 16x) / after (right, 32x), both shown at 96 px')
    for i, n in enumerate(names):
        x, y = (i % cols) * tw + 10, (i // cols) * th + 28
        img.alpha_composite(item_tile(before, n), (x, y))
        img.alpha_composite(item_tile(NEW_ASSETS, n), (x + 110, y))
        text(img, (x, y + 98), n[:34])
    img.save(os.path.join(out, 'before_after_items.png'))


def sheet_blocks(before, out):
    tw = 150
    img = Image.new('RGBA', (len(BLOCKS) * tw, 2 * 150 + 40), BG)
    text(img, (8, 6), 'blocks, GUI view: before (top) / after (bottom)')
    for row, root in enumerate((before, NEW_ASSETS)):
        use_assets(root)
        for i, n in enumerate(BLOCKS):
            img.alpha_composite(gui_block(n, 130), (i * tw + 10, 24 + row * 150))
            if row:
                text(img, (i * tw + 10, 24 + 2 * 150 - 8), n)
    img.save(os.path.join(out, 'before_after_blocks.png'))


def sheet_entities(before, out):
    tw = 170
    img = Image.new('RGBA', (len(MOBS) * tw, 2 * 200 + 40), BG)
    text(img, (8, 6), 'entities: before (top) / after (bottom); echo = pink silhouette layer')
    for row, root in enumerate((before, NEW_ASSETS)):
        use_assets(root)
        for i, (n, tint, k) in enumerate(MOBS):
            img.alpha_composite(mob_render(n, tint, k, 160, 196), (i * tw + 5, 24 + row * 200))
            if row:
                text(img, (i * tw + 10, 24 + 2 * 200 - 6), n)
    img.save(os.path.join(out, 'before_after_entities.png'))


def sheet_textures(before, out):
    """Raw texture sheets side by side (entity skins, block sheets, particles, gui)."""
    groups = [('entity', ['archivist', 'echo_strider', 'moment_replicant', 'residue', 'scar', 'echo_silhouette']),
              ('particle', None), ('gui', ['panel', 'slot', 'tags'])]
    img = Image.new('RGBA', (1500, 900), BG)
    y = 8
    for kind, names in groups:
        for col, root in enumerate((before, NEW_ASSETS)):
            d = os.path.join(root, 'mnemolith', 'textures', kind)
            ns = names or sorted(f[:-4] for f in os.listdir(d) if f.endswith('.png'))
            x = 8 + col * 750
            text(img, (x, y), '%s %s' % (kind, 'before' if col == 0 else 'after'))
            for n in ns:
                p = os.path.join(d, n + '.png')
                if not os.path.exists(p):
                    continue
                t = Image.open(p).convert('RGBA')
                k = max(1, 128 // max(t.size)) if kind != 'gui' else 1
                t = t.resize((t.width * k, t.height * k), Image.NEAREST) if kind != 'gui' else t.resize((min(t.width, 288) * 1, t.height), Image.NEAREST)
                if x + t.width > 8 + col * 750 + 740:
                    continue
                img.alpha_composite(t, (x, y + 14))
                x += t.width + 6
        y += 170
    img = img.crop((0, 0, 1500, y + 10))
    img.save(os.path.join(out, 'before_after_textures.png'))


def sheet_guide(before, out):
    for part, names in enumerate((guide.PAGES[:13], guide.PAGES[13:])):
        img = Image.new('RGBA', (2 * 512 + 30, len(names) * 262 + 24), BG)
        text(img, (8, 6), 'field guide: before (left, 256x128 shown 2x) / after (right, 512x256)')
        for i, n in enumerate(names):
            y = 24 + i * 262
            b = os.path.join(before, 'mnemolith', 'textures', 'gui', 'guide', n + '.png')
            if os.path.exists(b):
                im = Image.open(b).convert('RGBA')
                img.alpha_composite(im.resize((512, 256), Image.NEAREST), (8, y))
            img.alpha_composite(Image.open(os.path.join(NEW_ASSETS, 'mnemolith', 'textures', 'gui', 'guide', n + '.png')).convert('RGBA'), (528, y))
        img.save(os.path.join(out, 'before_after_guide_%d.png' % (part + 1)))


def sheet_inventory(out):
    """Inventory-style grid at 2x GUI scale (item 32 px), each item on the mod slot, plus the block items."""
    use_assets(NEW_ASSETS)
    from mnart import gui
    names = list(items.ITEMS) + BLOCKS
    cols = 9
    rows = (len(names) + cols - 1) // cols
    k = 2
    cell = 18 * k
    img = Image.new('RGBA', (cols * cell + 40, rows * cell + 40), (198, 198, 198, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([4, 4, img.width - 5, img.height - 5], outline=(85, 85, 85, 255), width=2)
    for i, n in enumerate(names):
        x, y = 20 + (i % cols) * cell, 20 + (i // cols) * cell
        d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(139, 139, 139, 255))
        d.line([x, y, x + cell - 1, y], fill=(55, 55, 55, 255), width=2)
        d.line([x, y, x, y + cell - 1], fill=(55, 55, 55, 255), width=2)
        d.line([x, y + cell - 1, x + cell - 1, y + cell - 1], fill=(255, 255, 255, 255), width=2)
        d.line([x + cell - 1, y, x + cell - 1, y + cell - 1], fill=(255, 255, 255, 255), width=2)
        if n in BLOCKS:
            ic = gui_block(n, 32)
        else:
            ic = Image.open(os.path.join(NEW_ASSETS, 'mnemolith', 'textures', 'item', n + '.png')).convert('RGBA')
        img.alpha_composite(ic, (x + 2, y + 2))
    big = img.resize((img.width * 2, img.height * 2), Image.NEAREST)
    big.save(os.path.join(out, 'gui_inventory_2x.png'))


def hand_first_person(name, w=384, h=216):
    """ItemInHandRenderer, right hand, no swing: translate(0.56, -0.52, -0.72) then the model's display transform."""
    s = R.Scene(w, h, ss=2, bg=(120, 160, 210, 255))
    s.ortho = False
    s.fov = 70
    m = R.load_model(_hand_model(name))
    dm = R.display_matrix(m['display'].get('firstperson_righthand'))
    s.add(R.model_quads(m), R.trans(0.56, -0.52, -0.72) @ dm @ R.trans(-0.5, -0.5, -0.5), 'entity')
    return s.render()


def _hand_model(name):
    if ':' in name:
        return name
    for cand in ('mnemolith:item/%s_in_hand' % name, 'mnemolith:item/' + name, 'mnemolith:block/' + name):
        try:
            R.load_model(cand)
            return cand
        except FileNotFoundError:
            pass
    raise FileNotFoundError(name)


def hand_third_person(name, w=200, h=240, yaw=200):
    """Held by the wanderer: arm.translateAndRotate, rotX(-90), rotY(180), translate(1/16, 2/16, -10/16), display."""
    s = R.Scene(w, h, ss=2)
    s.ortho_scale = 78
    s.view = R.rot_x(math.radians(10)) @ R.rot_y(math.radians(yaw))
    arm_rot = (-0.6, 0.1, 0.05)
    poses = {'right_arm': arm_rot}
    s.add(guide.mob_quads('wanderer', 0, 0, 0, 0, 1.0, poses), R.trans(0, -1.0, 0), 'entity')
    m = R.load_model(_hand_model(name))
    rx, ry, rz = arm_rot
    arm = R.trans(-5 / 16, 2 / 16, 0) @ R.rot_z(rz) @ R.rot_y(ry) @ R.rot_x(rx)
    mat = R.trans(0, -1.0, 0) @ R.trans(0, 1.5, 0) @ EM.FLIP @ arm @ R.rot_x(-math.pi / 2) @ R.rot_y(math.pi) \
        @ R.trans(1 / 16, 2 / 16, -10 / 16) @ R.display_matrix(m['display'].get('thirdperson_righthand')) @ R.trans(-0.5, -0.5, -0.5)
    s.add(R.model_quads(m), mat, 'entity')
    return s.render()


def sheet_hands(out, vanilla):
    use_assets(NEW_ASSETS)
    names = ['chronicle_lens', 'extraction_needle', 'imprint_slip', 'field_guide', 'relay_thread', 'mute_stone']
    prev = R.VANILLA_ASSETS
    if vanilla:   # scale reference: a vanilla handheld item through the same code path
        R.VANILLA_ASSETS = vanilla
        names.append('minecraft:item/iron_sword')
    img = Image.new('RGBA', (len(names) * 394, 216 + 250 + 40), BG)
    text(img, (8, 6), 'first person right hand (fov 70, 16:9, no bob/arm) / third person on the wanderer; lens and needle use the *_in_hand element models; last = vanilla reference')
    for i, n in enumerate(names):
        img.alpha_composite(hand_first_person(n), (i * 394 + 5, 22))
        img.alpha_composite(hand_third_person(n), (i * 394 + 100, 246))
        text(img, (i * 394 + 10, 22 + 216 + 250 - 4), n)
    R.VANILLA_ASSETS = prev
    img.save(os.path.join(out, 'in_hand.png'))


def sheet_world(out, vanilla):
    """Perspective scene at eye height with vanilla context blocks (if available) for scale and style contrast."""
    use_assets(NEW_ASSETS)
    R.VANILLA_ASSETS = vanilla or ''
    s = R.Scene(960, 540, ss=2, bg=(0, 0, 0, 0))
    s.ortho = False
    s.fov = 70
    s.view = R.look_at((0.2, 2.2, 6.3), (0.4, 0.4, -1.5))
    grass = (145, 189, 89)
    have_vanilla = bool(vanilla) and os.path.exists(os.path.join(vanilla, 'minecraft', 'models', 'block', 'grass_block.json'))

    def vb(model, x, y, z, tint=None):
        if have_vanilla:
            s.add_model('minecraft:block/' + model, R.trans(x + 0.5, y + 0.5, z + 0.5), light='block', tint=tint)
        else:
            s.add(guide.cube('turf_top' if model == 'grass_block' else 'stone', 'turf_side' if model == 'grass_block' else 'stone', x, y, z), np.eye(4))

    for x in range(-6, 7):
        for z in range(-9, 6):
            if (x, z) in ((2, -1), (3, -1)):
                continue
            vb('grass_block', x, -1, z, grass)
    for (x, z) in ((2, -1), (3, -1)):   # a dug pit showing the stratum
        vb('stone', x, -2, z)
    s.add_model('mnemolith:block/archival_stratum', R.trans(2.5, -1.5, -0.5), light='block')
    s.add_model('mnemolith:block/archival_stratum', R.trans(3.5, -1.5, -0.5), light='block')
    if have_vanilla:
        for (m, x, y, z) in (('oak_planks', -4, 0, -3), ('crafting_table', -3, 0, -3), ('bookshelf', -5, 0, -4), ('bookshelf', -5, 1, -4), ('stone', 4, 0, -5), ('cobblestone', 5, 0, -5)):
            vb(m, x, y, z)
    for (b, x, z, yaw) in (('mute_stone', -1, -1, 0), ('composition_reel', -2.2, 0.3, 30), ('resonator_trap', 1, -3, 0),
                           ('archive_vault_on', 0, -5, 0), ('scar_heart', 4, -7, 0), ('scar_glass', 3, -7, 0), ('scar_glass', 5, -7, 0)):
        s.add_model('mnemolith:block/' + b, R.trans(x + 0.5, 0.5, z + 0.5) @ R.rot_y(math.radians(yaw)), light='block')
    s.add(guide.mob_quads('archivist', -3.2, 0, -1.5, 160), np.eye(4), 'entity')
    s.add(guide.mob_quads('echo_strider', 2.2, 0, 1.0, 230), np.eye(4), 'entity')
    s.add(guide.mob_quads('moment_replicant', 1.6, 0, -6.0, 190), np.eye(4), 'entity')
    s.add(guide.mob_quads('residue', -1.0, 0.4, -6.0, 200), np.eye(4), 'entity', (255, 154, 92, 255))
    s.add(guide.mob_quads('echo', 0.3, 0, -2.2, 200), np.eye(4), 'entity', guide.ECHO_TINT)
    s.add(guide.mob_quads('scar', 6.0, 0.3, -8.0, 210, 2.0), np.eye(4), 'entity')
    for (it, x, z, yaw) in (('chronicle_lens', 0.9, 1.4, 20), ('extraction_needle', -0.4, 2.0, 70), ('imprint_slip', 1.6, 2.4, 140)):
        m = R.load_model('mnemolith:item/' + it)
        dm = R.display_matrix(m['display'].get('ground') if m['display'].get('ground') else {'scale': [0.5, 0.5, 0.5], 'translation': [0, 2, 0]})
        s.add(R.model_quads(m), R.trans(x, 0.25, z) @ R.rot_y(math.radians(yaw)) @ dm @ R.trans(-0.5, -0.5, -0.5), 'block')
    scene = s.render()
    sky = Image.new('RGBA', scene.size)
    top, bot = np.array([112, 150, 214]), np.array([196, 214, 236])
    grad = np.linspace(0, 1, scene.height)[:, None] * (bot - top) + top
    sky = Image.fromarray(np.dstack([np.repeat(grad[:, None, :], scene.width, 1).astype(np.uint8)[:, :, i] for i in range(3)] + [np.full(scene.size[::-1], 255, np.uint8)]), 'RGBA')
    sky.alpha_composite(scene)
    text(sky, (8, 6), 'in-world approximation: vanilla face shading, no lightmap/AO/fog' + ('' if have_vanilla else ' (no vanilla assets: stand-in ground)'), (20, 20, 30, 255))
    sky.save(os.path.join(out, 'in_world.png'))
    R.VANILLA_ASSETS = vanilla or ''


def sheet_blocks_detail(out):
    use_assets(NEW_ASSETS)
    names = BLOCKS + ['archive_vault_on']
    img = Image.new('RGBA', (4 * 300, 2 * 300 + 20), BG)
    for i, n in enumerate(names):
        s = R.Scene(300, 300, ss=3)
        s.ortho_scale = 190
        s.view = R.rot_x(math.radians(28)) @ R.rot_y(math.radians(35 + 90 * (i % 2)))
        s.add_model('mnemolith:block/' + n, np.eye(4), light='block')
        img.alpha_composite(s.render(), ((i % 4) * 300, (i // 4) * 300 + 20))
        text(img, ((i % 4) * 300 + 8, (i // 4) * 300 + 22), n)
    text(img, (8, 4), 'block models, world shading, alternate corners')
    img.save(os.path.join(out, 'blocks_detail.png'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--before', default='HEAD')
    ap.add_argument('--out', default=os.path.join(REPO, 'build', 'art-preview'))
    ap.add_argument('--vanilla', default=os.environ.get('MNART_VANILLA', ''))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    R.VANILLA_ASSETS = a.vanilla   # vanilla parents (block/block display etc.) when available
    before = extract_before(a.before)
    sheet_items(before, a.out)
    sheet_blocks(before, a.out)
    sheet_entities(before, a.out)
    sheet_textures(before, a.out)
    sheet_guide(before, a.out)
    sheet_inventory(a.out)
    sheet_blocks_detail(a.out)
    sheet_hands(a.out, a.vanilla)
    sheet_world(a.out, a.vanilla)
    print('previews in', a.out)


if __name__ == '__main__':
    main()
