#!/usr/bin/env python3
"""Bake Chaalen's CC-BY chair GLB into a compact entity mesh.

Minecraft cannot load GLB. This reads chair.glb (glTF binary), voxelizes the
photogrammetry shell, greedy-meshes the surface, and writes:

  assets/mnemolith/models/entity/pleading_chair.mesh
  assets/mnemolith/textures/entity/pleading_chair.png

Mesh space: feet at y=0, XZ centered, open side (the way a sitter looks) toward -Z.
Vertex colors multiply the flat plastic texture (255 = the texture color).

Usage: python3 tools/bake_pleading_chair.py /path/to/chair.glb
"""

import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MESH_OUT = ROOT / "src/main/resources/assets/mnemolith/models/entity/pleading_chair.mesh"
TEX_OUT = ROOT / "src/main/resources/assets/mnemolith/textures/entity/pleading_chair.png"

# Chair back about 1.15 blocks, so a player sits at a normal seat height.
TARGET_HEIGHT = 1.15
VOXEL_Y = 52
# Bright plastic from the texture's upper range. Vertex colors darken toward the photo.
PLASTIC = np.array([82, 198, 112], dtype=np.float32)


def load_glb(path: Path):
    data = path.read_bytes()
    off = 12
    length, _ = struct.unpack_from("<II", data, off)
    import json
    js = json.loads(data[off + 8 : off + 8 + length])
    off += 8 + length
    length, _ = struct.unpack_from("<II", data, off)
    blob = data[off + 8 : off + 8 + length]
    return js, blob


def read_vec(js, blob, acc_idx, dim):
    acc = js["accessors"][acc_idx]
    bv = js["bufferViews"][acc["bufferView"]]
    start = bv.get("byteOffset", 0)
    raw = blob[start : start + bv["byteLength"]]
    stride = bv.get("byteStride", dim * 4)
    offset = acc.get("byteOffset", 0)
    count = acc["count"]
    if stride == dim * 4:
        return np.frombuffer(raw, np.float32, count * dim, offset).reshape(count, dim).copy()
    out = np.empty((count, dim), np.float32)
    for i in range(count):
        out[i] = np.frombuffer(raw, np.float32, dim, offset + i * stride)
    return out


def main():
    glb = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/chair-assets/chair.glb")
    js, blob = load_glb(glb)
    prims = [(0, 2), (4, 6), (8, 10), (12, 14)]
    pos = np.concatenate([read_vec(js, blob, p, 3) for p, _ in prims])
    uv = np.concatenate([read_vec(js, blob, u, 2) for _, u in prims])

    # Parent rotations cancel; mesh Y is already up. Front of the seat is +Z.
    mn = pos.min(0)
    mx = pos.max(0)
    size = mx - mn
    scale = TARGET_HEIGHT / float(size[1])
    cell = float(size[1]) / VOXEL_Y
    dims = np.maximum(1, np.ceil(size / cell).astype(np.int32)) + 1
    nx, ny, nz = (int(dims[0]), int(dims[1]), int(dims[2]))
    print(f"grid {nx}x{ny}x{nz} cell {cell:.4f} scale {scale:.4f}")

    bv = js["bufferViews"][3]
    tex = np.asarray(Image.open(__import__("io").BytesIO(blob[bv["byteOffset"] : bv["byteOffset"] + bv["byteLength"]])).convert("RGB"))
    th, tw = tex.shape[:2]
    uu = np.clip((uv[:, 0] * (tw - 1)).astype(np.int32), 0, tw - 1)
    vv = np.clip(((1.0 - uv[:, 1]) * (th - 1)).astype(np.int32), 0, th - 1)
    cols = tex[vv, uu].astype(np.float32)

    occ = np.zeros((nx, ny, nz), np.uint8)
    col_sum = np.zeros((nx, ny, nz, 3), np.float32)
    col_n = np.zeros((nx, ny, nz), np.float32)
    ip = np.clip(((pos - mn) / cell).astype(np.int32), 0, [nx - 1, ny - 1, nz - 1])
    # Scatter-add colors. A python loop over 200k is fine.
    for i in range(len(ip)):
        x, y, z = int(ip[i, 0]), int(ip[i, 1]), int(ip[i, 2])
        occ[x, y, z] = 1
        col_sum[x, y, z] += cols[i]
        col_n[x, y, z] += 1.0

    # Drop specks, then close 1-voxel cracks in the shell.
    neigh = np.zeros_like(occ, np.uint8)
    for axis, shift in ((0, 1), (0, -1), (1, 1), (1, -1), (2, 1), (2, -1)):
        neigh += np.roll(occ, shift, axis)
    occ[neigh < 2] = 0
    dil = occ.copy()
    for axis, shift in ((0, 1), (0, -1), (1, 1), (1, -1), (2, 1), (2, -1)):
        dil = np.maximum(dil, np.roll(occ, shift, axis))
    # Erode back so the chair does not grow a block, but keep voxels that filled a crack
    # (original empty cells that became surrounded).
    er = dil.copy()
    for axis, shift in ((0, 1), (0, -1), (1, 1), (1, -1), (2, 1), (2, -1)):
        er = np.minimum(er, np.roll(dil, shift, axis))
    filled = (er == 1) & (occ == 0)
    occ = np.where(filled, np.uint8(1), occ)
    # Colors for filled cells: copy from a neighbor average later via the plastic fallback.
    print("occupied", int(occ.sum()))

    avg = np.zeros((nx, ny, nz, 3), np.float32)
    mask = col_n > 0
    avg[mask] = col_sum[mask] / col_n[mask, None]
    avg[~mask] = PLASTIC

    # Vertex color is a multiplier on PLASTIC: 255 keeps the texture, lower darkens.
    ratio = np.clip(avg / PLASTIC, 0.45, 1.0)
    vcol = np.clip(ratio * 255.0, 0, 255).astype(np.uint8)

    quads = []  # (axis, sign, x, y, z, w, h, r, g, b) in voxel coords, face on the +side of the cell

    def greedy(mask2, colors):
        """mask2 bool[h, w], colors uint8[h, w, 3]. Returns list of (y, x, h, w, rgb)."""
        h, w = mask2.shape
        used = np.zeros_like(mask2, np.bool_)
        rects = []
        for y in range(h):
            x = 0
            while x < w:
                if not mask2[y, x] or used[y, x]:
                    x += 1
                    continue
                color = colors[y, x]
                x2 = x + 1
                while x2 < w and mask2[y, x2] and not used[y, x2] and np.all(colors[y, x2] == color):
                    x2 += 1
                y2 = y + 1
                while y2 < h and np.all(mask2[y2, x:x2]) and not np.any(used[y2, x:x2]) and np.all(colors[y2, x:x2] == color):
                    y2 += 1
                used[y:y2, x:x2] = True
                rects.append((y, x, y2 - y, x2 - x, color))
                x = x2
        return rects

    # Faces: for each axis, a face exists where occ differs from the neighbor.
    # Store quads in block space after conversion.
    faces = 0
    for axis in range(3):
        for sign, delta in ((1, 1), (-1, -1)):
            # Compare occ with neighbor along axis.
            src = occ
            if delta == 1:
                a = src
                b = np.zeros_like(src)
                sl = [slice(None)] * 3
                sl[axis] = slice(0, -1)
                b[tuple(sl)] = src[tuple([slice(None) if i != axis else slice(1, None) for i in range(3)])]
                exposed = (a == 1) & (b == 0)
            else:
                a = src
                b = np.zeros_like(src)
                sl_src = [slice(None) if i != axis else slice(1, None) for i in range(3)]
                sl_dst = [slice(None) if i != axis else slice(0, -1) for i in range(3)]
                b[tuple(sl_dst)] = src[tuple(sl_src)]
                # For sign -1 the exposed face of cell i is when cell i is solid and i-1 is empty.
                # Rebuild simply:
                exposed = np.zeros_like(src, np.bool_)
                # cell is solid, previous along axis is empty
                idx = [slice(None)] * 3
                idx[axis] = slice(1, None)
                prev = [slice(None)] * 3
                prev[axis] = slice(0, -1)
                exposed[tuple(idx)] = (src[tuple(idx)] == 1) & (src[tuple(prev)] == 0)
                if axis == 0:
                    exposed[0, :, :] = src[0, :, :] == 1
                elif axis == 1:
                    exposed[:, 0, :] = src[:, 0, :] == 1
                else:
                    exposed[:, :, 0] = src[:, :, 0] == 1

            if delta == 1:
                exposed = (src == 1) & (b == 0)
                # outer positive boundary
                if axis == 0:
                    exposed[-1, :, :] = src[-1, :, :] == 1
                elif axis == 1:
                    exposed[:, -1, :] = src[:, -1, :] == 1
                else:
                    exposed[:, :, -1] = src[:, :, -1] == 1

            # Greedy per slice perpendicular to axis.
            for i in range(src.shape[axis]):
                if axis == 0:
                    slc = exposed[i, :, :]
                    cols_sl = vcol[i, :, :]
                elif axis == 1:
                    slc = exposed[:, i, :]
                    cols_sl = vcol[:, i, :]
                else:
                    slc = exposed[:, :, i]
                    cols_sl = vcol[:, :, i]
                if not np.any(slc):
                    continue
                for y0, x0, h, w, color in greedy(slc, cols_sl):
                    faces += 1
                    quads.append((axis, sign, i, y0, x0, h, w, int(color[0]), int(color[1]), int(color[2])))

    print("quads", faces)

    # Convert quads to triangles in block space.
    # Voxel (x,y,z) occupies [x,x+1) * cell, then * scale, Y from 0, XZ centered.
    # After that, flip Z around 0 so the open front (+Z in the scan) faces -Z.
    center_x = (float(size[0]) * 0.5) * scale
    center_z = (float(size[2]) * 0.5) * scale

    def corner(vx, vy, vz):
        x = vx * cell * scale - center_x
        y = vy * cell * scale
        z = vz * cell * scale - center_z
        z = -z
        return x, y, z

    # axis slice index i, in-plane (y0, x0) map to the other two axes.
    # For axis 0 (X): plane axes are Y (rows) and Z (cols) from greedy(slc) where slc = exposed[i,:,:] so row=Y col=Z.
    # For axis 1 (Y): slc = exposed[:, i, :] row=X col=Z
    # For axis 2 (Z): slc = exposed[:, :, i] row=X col=Y
    tris = []
    seat_samples = []
    for axis, sign, i, r0, c0, rh, rw, cr, cg, cb in quads:
        if axis == 0:
            x0 = i + (1 if sign > 0 else 0)
            y0, z0 = r0, c0
            y1, z1 = r0 + rh, c0 + rw
            corners = [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)]
            normal = (-1.0 if sign < 0 else 1.0, 0.0, 0.0)
        elif axis == 1:
            y0 = i + (1 if sign > 0 else 0)
            x0, z0 = r0, c0
            x1, z1 = r0 + rh, c0 + rw
            corners = [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)]
            normal = (0.0, -1.0 if sign < 0 else 1.0, 0.0)
        else:
            z0 = i + (1 if sign > 0 else 0)
            x0, y0 = r0, c0
            x1, y1 = r0 + rh, c0 + rw
            corners = [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)]
            normal = (0.0, 0.0, -1.0 if sign < 0 else 1.0)
        # Flip Z mirrors the normal's Z and the winding.
        pts = [corner(*p) for p in corners]
        nx, ny_, nz = normal
        nz = -nz
        # Winding: original corners are CCW when looking along +normal before the Z flip.
        # Z flip reverses winding, so swap.
        order = (0, 2, 1, 0, 3, 2) if True else (0, 1, 2, 0, 2, 3)
        # After Z negation, use the swapped winding so normals still point outward.
        for a, b, c in ((0, 2, 1), (0, 3, 2)):
            tris.append((pts[a], pts[b], pts[c], (cr, cg, cb), (nx, ny_, nz)))
        if axis == 1 and sign > 0:
            cy = (i + 1) * cell * scale
            if 0.35 < cy < 0.7:
                seat_samples.append(cy)

    seat = float(np.median(seat_samples)) if seat_samples else 0.48
    print(f"tris {len(tris)} seat {seat:.3f}")

    # Bounds check
    allp = np.array([p for t in tris for p in t[:3]])
    print("bounds min", allp.min(0), "max", allp.max(0))

    MESH_OUT.parent.mkdir(parents=True, exist_ok=True)
    with MESH_OUT.open("wb") as f:
        f.write(b"PCH1")
        f.write(struct.pack("<fI", seat, len(tris) * 3))
        for (a, b, c, col, nrm) in tris:
            for p in (a, b, c):
                f.write(struct.pack("<fffBBBB", p[0], p[1], p[2], col[0], col[1], col[2], 255))
                f.write(struct.pack("<fff", nrm[0], nrm[1], nrm[2]))
    print("wrote", MESH_OUT, MESH_OUT.stat().st_size)

    Image.new("RGB", (16, 16), tuple(int(v) for v in PLASTIC)).save(TEX_OUT)
    print("wrote", TEX_OUT)

    # Orthographic previews for a visual check.
    preview(tris, "/tmp/chair-baked-front.png", axis_u=0, axis_v=1, flip_u=False)
    preview(tris, "/tmp/chair-baked-side.png", axis_u=2, axis_v=1, flip_u=True)
    preview(tris, "/tmp/chair-baked-top.png", axis_u=0, axis_v=2, flip_u=False)
    print("seat_y", f"{seat:.4f}")


def preview(tris, path, axis_u, axis_v, flip_u):
    size = 420
    img = np.zeros((size, size, 3), np.uint8) + 24
    zbuf = np.full((size, size), 1e9, np.float32)
    depth_axis = ({0, 1, 2} - {axis_u, axis_v}).pop()
    pts = []
    for a, b, c, col, nrm in tris:
        pts.extend((a, b, c))
    arr = np.array(pts)
    umin, umax = arr[:, axis_u].min(), arr[:, axis_u].max()
    vmin, vmax = arr[:, axis_v].min(), arr[:, axis_v].max()
    span = max(umax - umin, vmax - vmin, 1e-4)
    s = (size - 24) / span

    def pix(p):
        u = (p[axis_u] - umin) * s + 12
        if flip_u:
            u = size - u
        v = size - 12 - (p[axis_v] - vmin) * s
        return u, v

    for a, b, c, col, nrm in tris:
        poly = [pix(a), pix(b), pix(c)]
        d = (a[depth_axis] + b[depth_axis] + c[depth_axis]) / 3.0
        # Front views look along +depth from the smaller side (painter uses zbuf min).
        xs = [p[0] for p in poly]
        ys = [p[1] for p in poly]
        minx, maxx = int(max(0, min(xs))), int(min(size - 1, max(xs)))
        miny, maxy = int(max(0, min(ys))), int(min(size - 1, max(ys)))
        if maxx - minx > 80 or maxy - miny > 80:
            continue
        for y in range(miny, maxy + 1):
            for x in range(minx, maxx + 1):
                if _inside(x + 0.5, y + 0.5, poly) and d < zbuf[y, x]:
                    zbuf[y, x] = d
                    shade = 0.55 + 0.45 * abs(nrm[axis_v])
                    img[y, x] = np.clip(np.array(col) * shade * (PLASTIC / 255.0), 0, 255)

    Image.fromarray(img).save(path)
    print("preview", path)


def _inside(x, y, poly):
    # barycentric of triangle
    (x1, y1), (x2, y2), (x3, y3) = poly
    den = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3)
    if abs(den) < 1e-6:
        return False
    a = ((y2 - y3) * (x - x3) + (x3 - x2) * (y - y3)) / den
    b = ((y3 - y1) * (x - x3) + (x1 - x3) * (y - y3)) / den
    c = 1 - a - b
    return a >= 0 and b >= 0 and c >= 0


if __name__ == "__main__":
    main()
