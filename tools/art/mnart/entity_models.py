"""Part trees transcribed from the Java LayerDefinitions (client/model/*Model.java), for offline renders only.
Keep in sync by hand when a model changes; build.py --check compares nothing here with the game."""
import math

import numpy as np

from .render3d import entity_box_quads, trans, rot_x, rot_y, rot_z

FLIP = np.diag([-1.0, -1.0, 1.0, 1.0])


def part(name, pivot=(0, 0, 0), cubes=(), children=(), rot=(0, 0, 0)):
    return {'name': name, 'pivot': pivot, 'cubes': list(cubes), 'children': list(children), 'rot': rot}


def archivist():
    return [
        part('body', (0, 6, 0), [(0, 16, -5, 0, -3, 10, 10, 6)], [
            part('hood', (0, 0, 1), [(0, 0, -4, -6, -4, 8, 6, 8)]),
            part('cloak', (0, -1, 2), [(0, 34, -6, 0, 0, 12, 14, 2)]),
            part('satchel', (4, 4, 2), [(32, 0, 0, 0, 0, 3, 5, 3)]),
            part('arm_left', (5, 1, 0), [(32, 16, 0, 0, -1, 2, 9, 2)], rot=(-0.2, 0, -0.08)),
            part('arm_right', (-5, 1, 0), [(32, 16, -2, 0, -1, 2, 9, 2)], rot=(0.25, 0, 0.08)),
        ]),
        part('leg_left', (2, 16, 0), [(32, 28, -1, 0, -1, 2, 8, 2)], rot=(0.2, 0, 0)),
        part('leg_right', (-2, 16, 0), [(32, 28, -1, 0, -1, 2, 8, 2)], rot=(-0.2, 0, 0)),
    ]


def echo_strider():
    leg = [(0, 34, -1, 0, -1, 2, 8, 2)]
    return [
        part('body', (0, 15, 0), [(0, 0, -10, -2, -3, 20, 4, 6)], [
            part('head', (0, -1, -3), [(0, 12, -3, -3, -3, 6, 5, 3)]),
            part('fin_left', (10, 0, -1), [(0, 22, 0, -2, -4, 1, 3, 8)], rot=(0, 0, -0.35)),
            part('fin_right', (-10, 0, -1), [(20, 22, -1, -2, -4, 1, 3, 8)], rot=(0, 0, 0.35)),
        ]),
        part('leg0', (-6, 16, -3), leg, rot=(0.3, 0, 0)), part('leg1', (6, 16, -3), leg, rot=(-0.3, 0, 0)),
        part('leg2', (-6, 16, 3), leg, rot=(-0.3, 0, 0)), part('leg3', (6, 16, 3), leg, rot=(0.3, 0, 0)),
    ]


def moment_replicant(shell=True):
    parts = [
        part('head', (0, 0, 0), [(0, 0, -3, -6, -3, 6, 6, 6)]),
        part('body', (0, 0, 0), [(0, 14, -2, 0, -1, 4, 12, 3)]),
        part('arm_left', (2, 1, 0), [(16, 14, 0, 0, -1, 2, 11, 2)], rot=(-1.1, 0, 0)),
        part('arm_right', (-2, 1, 0), [(16, 14, -2, 0, -1, 2, 11, 2)], rot=(-1.1, 0, 0)),
        part('leg_left', (1, 12, 0), [(28, 0, -1, 0, -1, 2, 12, 2)]),
        part('leg_right', (-1, 12, 0), [(28, 0, -1, 0, -1, 2, 12, 2)]),
    ]
    if shell:
        parts += [
            part('echo_head', (0.8, 0, 2), [(32, 16, -3, -6, -3, 6, 6, 6)]),
            part('echo_body', (0.8, 0, 2), [(32, 30, -2, 0, -1, 4, 12, 3)]),
            part('echo_arm_left', (2.8, 1, 2), [(48, 0, 0, 0, -1, 2, 11, 2)], rot=(-2.4, 0, 0)),
            part('echo_arm_right', (-1.2, 1, 2), [(48, 0, -2, 0, -1, 2, 11, 2)], rot=(-2.4, 0, 0)),
        ]
    return parts


def residue():
    return [part('figure', (0, 0, 0), [], [
        part('head', (0, 4, 0), [(0, 0, -3, -6, -3, 6, 6, 6)]),
        part('torso', (0, 4, 0), [(0, 14, -3, 0, -1.5, 6, 8, 3)]),
        part('arm', (3, 4.5, 0), [(24, 0, 0, 0, -1, 2, 9, 2)], rot=(-0.3, 0, -0.2)),
        part('wisp_top', (0, 12, 0), [(0, 28, -2, 0, -1, 4, 4, 2)], [
            part('wisp_mid', (0, 4, 0), [(0, 36, -1.5, 0, -1, 3, 4, 2)], [
                part('wisp_tip', (0, 4, 0), [(0, 44, -1, 0, -0.5, 2, 4, 1)], rot=(0.3, 0, 0))], rot=(0.25, 0, 0))], rot=(0.15, 0, 0)),
    ])] + [part('shard_%d' % i, (math.cos(i * 2.1) * 7, 8 + i, math.sin(i * 2.1) * 7), [(40, 0, -1, -1.5, -0.5, 2, 3, 1)], rot=(0, i, 0.4)) for i in range(3)]


def player():
    return [
        part('head', (0, 0, 0), [(0, 0, -4, -8, -4, 8, 8, 8)]),
        part('body', (0, 0, 0), [(16, 16, -4, 0, -2, 8, 12, 4)]),
        part('right_arm', (-5, 2, 0), [(40, 16, -3, -2, -2, 4, 12, 4)], rot=(-0.35, 0, 0.05)),
        part('left_arm', (5, 2, 0), [(32, 48, -1, -2, -2, 4, 12, 4)], rot=(0.3, 0, -0.05)),
        part('right_leg', (-1.9, 12, 0), [(0, 16, -2, 0, -2, 4, 12, 4)], rot=(0.3, 0, 0)),
        part('left_leg', (1.9, 12, 0), [(16, 48, -2, 0, -2, 4, 12, 4)], rot=(-0.3, 0, 0)),
    ]


def quads(parts, tex_id, poses=None, scale_k=1.0):
    """World-space quads (block units, feet at y=0, facing -z) for a part list."""
    out = []
    poses = poses or {}

    def walk(p, parent):
        rx, ry, rz = poses.get(p['name'], p['rot'])
        px, py, pz = p['pivot']
        m_model = trans(px / 16, py / 16, pz / 16) @ rot_z(rz) @ rot_y(ry) @ rot_x(rx)
        mat = parent @ FLIP @ m_model @ np.linalg.inv(FLIP)
        for (u, v, x, y, z, w, h, d) in p['cubes']:
            for q in entity_box_quads(u, v, x, y, z, w, h, d, tex_id):
                q.pts = [tuple((mat @ np.array([*pt, 1.0]))[:3]) for pt in q.pts]
                q.normal = mat[:3, :3] @ q.normal
                out.append(q)
        for c in p['children']:
            walk(c, mat)
    root = trans(0, 1.5 * scale_k, 0) @ np.diag([scale_k, scale_k, scale_k, 1.0])
    for p in parts:
        walk(p, root)
    return out


MODELS = {
    'archivist': (archivist, 'mnemolith:entity/archivist'),
    'echo_strider': (echo_strider, 'mnemolith:entity/echo_strider'),
    'moment_replicant': (moment_replicant, 'mnemolith:entity/moment_replicant'),
    'residue': (residue, 'mnemolith:entity/residue'),
}
