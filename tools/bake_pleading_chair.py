#!/usr/bin/env python3
"""Turn Chaalen's CC-BY chair GLB into the entity mesh Minecraft can draw.

Sketchfab's USDZ, glTF, and resized GLB downloads are the same scan. Minecraft
loads none of them. This reads the GLB already in the repo, keeps its shape and
texture, and writes a smaller triangle list plus a PNG:

  assets/mnemolith/models/entity/pleading_chair.mesh
  assets/mnemolith/textures/entity/pleading_chair.png

Mesh space: feet at y=0, XZ centered, open side (the way a sitter looks) toward -Z.
UVs use Minecraft's top-left origin. Vertex colors are not stored; the texture is the color.

Usage: python tools/bake_pleading_chair.py [chair.glb] [texture.jpeg]
"""

import json
import struct
import sys
import tempfile
from pathlib import Path

import fast_simplification
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GLB = ROOT / "src/main/resources/assets/mnemolith/source/model.glb"
DEFAULT_TEX = ROOT / "src/main/resources/assets/mnemolith/textures/gltf_embedded_0.jpeg"
MESH_OUT = ROOT / "src/main/resources/assets/mnemolith/models/entity/pleading_chair.mesh"
TEX_OUT = ROOT / "src/main/resources/assets/mnemolith/textures/entity/pleading_chair.png"

# Chair back about 1.15 blocks, so a player sits at a normal seat height.
TARGET_HEIGHT = 1.15
# Asked-for size. The reducer stops higher when further collapses would chew the shell.
TARGET_TRIS = 20000
PREVIEW = Path(tempfile.gettempdir()) / "mnemolith-chair-preview"


def load_glb(path: Path):
    data = path.read_bytes()
    if data[:4] != b"glTF":
        raise SystemExit(f"not a glb: {path}")
    off = 12
    length, _ = struct.unpack_from("<II", data, off)
    js = json.loads(data[off + 8 : off + 8 + length])
    off += 8 + length
    length, _ = struct.unpack_from("<II", data, off)
    blob = data[off + 8 : off + 8 + length]
    return js, blob


def read_accessor(js, blob, index):
    acc = js["accessors"][index]
    bv = js["bufferViews"][acc["bufferView"]]
    start = bv.get("byteOffset", 0) + acc.get("byteOffset", 0)
    count = acc["count"]
    comp = acc["componentType"]
    dims = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[acc["type"]]
    dtype = {5121: np.uint8, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}[comp]
    width = np.dtype(dtype).itemsize * dims
    stride = bv.get("byteStride", width)
    raw = blob[start : start + bv["byteLength"] - acc.get("byteOffset", 0)]
    if stride == width:
        return np.frombuffer(raw, dtype, count * dims).reshape(count, dims).copy()
    out = np.empty((count, dims), dtype)
    for i in range(count):
        out[i] = np.frombuffer(raw, dtype, dims, i * stride)
    return out


def face_normals(points, tris):
    a = points[tris[:, 0]]
    b = points[tris[:, 1]]
    c = points[tris[:, 2]]
    n = np.cross(b - a, c - a)
    length = np.linalg.norm(n, axis=1)
    length[length < 1e-12] = 1.0
    return n / length[:, None]


def smooth_normals(points, tris):
    n = face_normals(points, tris)
    acc = np.zeros_like(points)
    for k in range(3):
        np.add.at(acc, tris[:, k], n)
    length = np.linalg.norm(acc, axis=1)
    length[length < 1e-12] = 1.0
    return acc / length[:, None]


def orient(points):
    """Feet on y=0, height TARGET_HEIGHT, back toward +Z so the open side faces -Z."""
    mn = points.min(0)
    mx = points.max(0)
    scale = TARGET_HEIGHT / float(mx[1] - mn[1])
    points = (points - mn) * scale
    center = points.mean(0)
    high = points[points[:, 1] > TARGET_HEIGHT * 0.72]
    if len(high) < 30:
        high = points[points[:, 1] > TARGET_HEIGHT * 0.55]
    back = high.mean(0) - center
    angle = np.arctan2(back[0], back[2])
    cos, sin = np.cos(angle), np.sin(angle)
    x = points[:, 0] * cos + points[:, 2] * sin
    z = -points[:, 0] * sin + points[:, 2] * cos
    points = np.column_stack((x, points[:, 1], z))
    points[:, 0] -= (points[:, 0].min() + points[:, 0].max()) * 0.5
    points[:, 2] -= (points[:, 2].min() + points[:, 2].max()) * 0.5
    return points


def seat_height(points, tris, normals):
    up = normals[:, 1] > 0.65
    mid = (points[tris].mean(1)[:, 1] > 0.28) & (points[tris].mean(1)[:, 1] < 0.72)
    ys = points[tris].mean(1)[up & mid, 1]
    if len(ys) == 0:
        return 0.48
    return float(np.median(ys))


def preview(points, tris, uvs, texture, path, axis_u, axis_v, flip_u):
    size = 520
    img = np.zeros((size, size, 3), np.uint8) + 28
    zbuf = np.full((size, size), 1e9, np.float32)
    depth = ({0, 1, 2} - {axis_u, axis_v}).pop()
    umin, umax = points[:, axis_u].min(), points[:, axis_u].max()
    vmin, vmax = points[:, axis_v].min(), points[:, axis_v].max()
    span = max(umax - umin, vmax - vmin, 1e-4)
    s = (size - 28) / span
    th, tw = texture.shape[:2]

    def pix(p):
        u = (p[axis_u] - umin) * s + 14
        if flip_u:
            u = size - u
        v = size - 14 - (p[axis_v] - vmin) * s
        return u, v

    for tri in tris:
        poly = [pix(points[i]) for i in tri]
        d = float(points[tri, depth].mean())
        xs = [p[0] for p in poly]
        ys = [p[1] for p in poly]
        minx, maxx = int(max(0, min(xs))), int(min(size - 1, max(xs) + 1))
        miny, maxy = int(max(0, min(ys))), int(min(size - 1, max(ys) + 1))
        (x1, y1), (x2, y2), (x3, y3) = poly
        den = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3)
        if abs(den) < 1e-6:
            continue
        uv = uvs[tri]
        for y in range(miny, maxy + 1):
            for x in range(minx, maxx + 1):
                px, py = x + 0.5, y + 0.5
                a = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / den
                b = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / den
                c = 1.0 - a - b
                if a >= -0.001 and b >= -0.001 and c >= -0.001 and d < zbuf[y, x]:
                    zbuf[y, x] = d
                    uu = a * uv[0, 0] + b * uv[1, 0] + c * uv[2, 0]
                    vv = a * uv[0, 1] + b * uv[1, 1] + c * uv[2, 1]
                    tx = int(np.clip(uu, 0, 1) * (tw - 1))
                    ty = int(np.clip(vv, 0, 1) * (th - 1))
                    img[y, x] = texture[ty, tx]
    Image.fromarray(img).save(path)


def main():
    glb_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_GLB
    tex_path = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_TEX
    js, blob = load_glb(glb_path)
    prim = js["meshes"][0]["primitives"][0]
    attrs = prim["attributes"]
    pos = read_accessor(js, blob, attrs["POSITION"]).astype(np.float64)
    uv = read_accessor(js, blob, attrs["TEXCOORD_0"]).astype(np.float64)
    idx = read_accessor(js, blob, prim["indices"]).reshape(-1).astype(np.int32)
    if len(idx) % 3:
        raise SystemExit("index buffer is not triangles")
    tris = idx.reshape(-1, 3)
    print(f"source verts {len(pos)} tris {len(tris)}")

    # glTF v=0 is the bottom of the image. Minecraft's v=0 is the top.
    uv[:, 1] = 1.0 - uv[:, 1]

    dec_points, dec_tris, collapses = fast_simplification.simplify(
        pos, tris, target_count=TARGET_TRIS, agg=2.0, return_collapses=True
    )
    dec_points, dec_tris, mapping = fast_simplification.replay_simplification(pos, tris, collapses)
    dec_points = np.asarray(dec_points, np.float64)
    dec_tris = np.asarray(dec_tris, np.int32)
    print(f"simplified verts {len(dec_points)} tris {len(dec_tris)}")

    best = np.full(len(dec_points), np.inf)
    out_uv = np.zeros((len(dec_points), 2), np.float64)
    for old, new in enumerate(mapping):
        if new < 0:
            continue
        delta = pos[old] - dec_points[new]
        dist = float(delta @ delta)
        if dist < best[new]:
            best[new] = dist
            out_uv[new] = uv[old]

    dec_points = orient(dec_points)
    normals = smooth_normals(dec_points, dec_tris)
    seat = seat_height(dec_points, dec_tris, face_normals(dec_points, dec_tris))
    print(f"bounds min {dec_points.min(0)} max {dec_points.max(0)} seat {seat:.4f}")

    # Non-indexed, the same layout the renderer walks every frame.
    flat = dec_tris.reshape(-1)
    verts = dec_points[flat]
    uvs = out_uv[flat]
    norms = normals[flat]

    MESH_OUT.parent.mkdir(parents=True, exist_ok=True)
    with MESH_OUT.open("wb") as f:
        f.write(b"PCH2")
        f.write(struct.pack("<fI", seat, len(verts)))
        for i in range(len(verts)):
            p, t, n = verts[i], uvs[i], norms[i]
            f.write(struct.pack("<fffff", p[0], p[1], p[2], t[0], t[1]))
            f.write(struct.pack("<fff", n[0], n[1], n[2]))
    print("wrote", MESH_OUT, MESH_OUT.stat().st_size)

    image = Image.open(tex_path).convert("RGB")
    image.save(TEX_OUT, optimize=True)
    print("wrote", TEX_OUT, TEX_OUT.stat().st_size, image.size)

    texture = np.asarray(image)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    preview(dec_points, dec_tris, out_uv, texture, PREVIEW / "front.png", 0, 1, False)
    preview(dec_points, dec_tris, out_uv, texture, PREVIEW / "side.png", 2, 1, True)
    preview(dec_points, dec_tris, out_uv, texture, PREVIEW / "top.png", 0, 2, False)
    print("seat_y", f"{seat:.4f}")


if __name__ == "__main__":
    main()
