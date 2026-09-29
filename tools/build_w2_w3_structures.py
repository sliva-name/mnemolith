#!/usr/bin/env python3
"""Build W2/W3 structure NBTs under data/mnemolith/structure/.

Multi-room buildings (not single hollow boxes). Run from repo root:
    python3 tools/build_w2_w3_structures.py
"""
from __future__ import annotations

import gzip
import os
import struct
from collections import OrderedDict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'src/main/resources/data/mnemolith/structure')


def tag_string(s: str) -> bytes:
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def named(tid: int, name: str, payload: bytes) -> bytes:
    return bytes([tid]) + tag_string(name) + payload


def int_list(values: list[int]) -> bytes:
    return bytes([3]) + struct.pack('>i', len(values)) + b''.join(struct.pack('>i', v) for v in values)


def compound_payload(items: list[bytes]) -> bytes:
    return b''.join(items) + b'\x00'


def compound_list(items: list[bytes]) -> bytes:
    return bytes([10 if items else 0]) + struct.pack('>i', len(items)) + b''.join(i + b'\x00' for i in items)


def string_payload(s: str) -> bytes:
    return tag_string(s)


def block_state(name: str, props: dict[str, str] | None = None) -> bytes:
    parts = [named(8, 'Name', string_payload(name))]
    if props:
        prop_tags = [named(8, k, string_payload(v)) for k, v in props.items()]
        parts.append(named(10, 'Properties', compound_payload(prop_tags)))
    return b''.join(parts)


def block_entry(state: int, pos: tuple[int, int, int], nbt: bytes | None = None) -> bytes:
    parts = [
        named(3, 'state', struct.pack('>i', state)),
        named(9, 'pos', int_list(list(pos))),
    ]
    if nbt is not None:
        parts.append(named(10, 'nbt', nbt))
    return b''.join(parts)


def chest_nbt(loot: str) -> bytes:
    return compound_payload([
        named(8, 'LootTable', string_payload(loot)),
        named(8, 'id', string_payload('minecraft:chest')),
    ])


def write_structure(path: str, size: tuple[int, int, int], palette: list[bytes], blocks: list[bytes]) -> None:
    root = b''.join([
        named(3, 'DataVersion', struct.pack('>i', 4903)),
        named(9, 'size', int_list(list(size))),
        named(9, 'palette', compound_list(palette)),
        named(9, 'blocks', compound_list(blocks)),
        named(9, 'entities', compound_list([])),
    ])
    data = named(10, '', root + b'\x00')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(gzip.compress(data, mtime=0))
    print('wrote', path, 'size', size, 'blocks', len(blocks), 'palette', len(palette))


class Builder:
    def __init__(self, sx: int, sy: int, sz: int):
        self.sx, self.sy, self.sz = sx, sy, sz
        self.palette_index: OrderedDict[tuple, int] = OrderedDict()
        self.palette: list[bytes] = []
        self.blocks: list[bytes] = []
        self.air = self.state('minecraft:air')

    def state(self, name: str, props: dict[str, str] | None = None) -> int:
        key = (name, tuple(sorted((props or {}).items())))
        if key not in self.palette_index:
            self.palette_index[key] = len(self.palette)
            self.palette.append(block_state(name, props))
        return self.palette_index[key]

    def set(self, x: int, y: int, z: int, state: int, nbt: bytes | None = None) -> None:
        if not (0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz):
            return
        self.blocks.append(block_entry(state, (x, y, z), nbt))

    def fill(self, x0, y0, z0, x1, y1, z1, state: int) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, state)

    def box(self, x0, y0, z0, x1, y1, z1, wall: int, fill: int | None = None) -> None:
        if fill is not None:
            self.fill(x0, y0, z0, x1, y1, z1, fill)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.set(x, y0, z, wall)
                self.set(x, y1, z, wall)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, wall)
                self.set(x, y, z1, wall)
            for z in range(z0, z1 + 1):
                self.set(x0, y, z, wall)
                self.set(x1, y, z, wall)

    def doorway(self, x: int, y: int, z: int, facing: str = 'z') -> None:
        """2-high doorway carved to air."""
        self.set(x, y, z, self.air)
        self.set(x, y + 1, z, self.air)
        if facing == 'x':
            self.set(x, y, z, self.air)
        # also clear a second cell for wider passages when needed by caller

    def write(self, rel: str) -> None:
        write_structure(os.path.join(OUT, rel), (self.sx, self.sy, self.sz), self.palette, self.blocks)


def flooded_archive() -> None:
    """W2 underground: antechamber → flooded hall → vault shrine room (3 rooms)."""
    b = Builder(17, 8, 13)
    deep = b.state('minecraft:deepslate_bricks')
    tiles = b.state('minecraft:deepslate_tiles')
    stratum = b.state('mnemolith:archival_stratum')
    bricks = b.state('mnemolith:archival_stratum_bricks')
    water = b.state('minecraft:water', {'level': '0'})
    air = b.air
    chest = b.state('minecraft:chest', {'facing': 'south', 'type': 'single', 'waterlogged': 'false'})
    chest2 = b.state('minecraft:chest', {'facing': 'east', 'type': 'single', 'waterlogged': 'false'})
    vault = b.state('mnemolith:archive_vault')
    shrine = b.state('mnemolith:archive_shrine', {'challenged': 'false', 'claimed': 'false'})
    stairs = b.state('minecraft:deepslate_brick_stairs', {
        'facing': 'east', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'
    })
    # clear volume
    b.fill(0, 0, 0, 16, 7, 12, air)
    # --- room 1: dry antechamber (west) ---
    b.box(0, 0, 0, 5, 6, 12, deep, air)
    b.fill(1, 0, 1, 4, 0, 11, tiles)
    for y in range(1, 5):
        b.set(1, y, 1, stratum)
        b.set(1, y, 11, stratum)
        b.set(4, y, 1, stratum)
        b.set(4, y, 11, stratum)
    b.set(2, 1, 10, chest2, chest_nbt('mnemolith:chests/flooded_archive'))
    # entrance from west
    b.set(0, 1, 6, air)
    b.set(0, 2, 6, air)
    # --- corridor / doorway into flooded hall ---
    b.fill(5, 1, 5, 5, 3, 7, air)
    # --- room 2: flooded hall (center) ---
    b.box(5, 0, 0, 11, 6, 12, deep, air)
    b.fill(6, 0, 1, 10, 0, 11, tiles)
    b.fill(6, 1, 1, 10, 1, 11, water)
    # dry walkway along north wall
    b.fill(6, 1, 1, 10, 1, 2, tiles)
    b.fill(6, 1, 10, 10, 1, 11, tiles)
    for x in (6, 10):
        for z in range(3, 10):
            b.set(x, 2, z, stratum)
    # pillars
    for z in (3, 9):
        b.fill(8, 1, z, 8, 4, z, bricks)
    # doorway into vault room
    b.fill(11, 1, 5, 11, 3, 7, air)
    # --- room 3: vault / shrine chamber (east, dry ledge) ---
    b.box(11, 0, 0, 16, 6, 12, deep, air)
    b.fill(12, 0, 1, 15, 0, 11, bricks)
    b.fill(12, 1, 1, 15, 1, 11, air)
    # raised dais
    b.fill(13, 1, 5, 15, 1, 8, tiles)
    b.set(14, 2, 6, vault)
    b.set(14, 2, 7, shrine)
    b.set(13, 2, 6, chest, chest_nbt('mnemolith:chests/flooded_archive'))
    b.set(12, 1, 6, stairs)
    # ceiling lanterns via hanging soul lanterns
    lantern = b.state('minecraft:soul_lantern', {'hanging': 'true', 'waterlogged': 'false'})
    b.set(3, 5, 6, lantern)
    b.set(8, 5, 6, lantern)
    b.set(14, 5, 6, lantern)
    b.write('flooded_archive.nbt')


def hush_chapel() -> None:
    """W2 surface: narthex → nave → sanctuary (3 rooms)."""
    b = Builder(15, 7, 11)
    mute = b.state('mnemolith:mute_stone')
    mute_brick = b.state('mnemolith:mute_stone_bricks')
    wall = b.state('mnemolith:mute_stone_wall', {
        'east': 'none', 'north': 'none', 'south': 'none', 'up': 'true', 'waterlogged': 'false', 'west': 'none'
    })
    air = b.air
    chest = b.state('minecraft:chest', {'facing': 'north', 'type': 'single', 'waterlogged': 'false'})
    chest2 = b.state('minecraft:chest', {'facing': 'west', 'type': 'single', 'waterlogged': 'false'})
    lantern = b.state('minecraft:soul_lantern', {'hanging': 'true', 'waterlogged': 'false'})
    shrine = b.state('mnemolith:archive_shrine', {'challenged': 'false', 'claimed': 'false'})
    slab = b.state('mnemolith:mute_stone_slab', {'type': 'bottom', 'waterlogged': 'false'})
    b.fill(0, 0, 0, 14, 6, 10, air)
    # --- narthex (south entry) ---
    b.box(4, 0, 0, 10, 5, 3, mute_brick, air)
    b.fill(5, 0, 1, 9, 0, 2, mute)
    b.set(7, 1, 0, air)
    b.set(7, 2, 0, air)
    # doorway to nave
    b.fill(6, 1, 3, 8, 3, 3, air)
    # --- nave (center hall with pews as mute walls) ---
    b.box(2, 0, 3, 12, 6, 7, mute_brick, air)
    b.fill(3, 0, 4, 11, 0, 6, mute)
    # pew-like side benches
    for z in (4, 6):
        b.fill(3, 1, z, 4, 1, z, slab)
        b.fill(10, 1, z, 11, 1, z, slab)
    b.set(3, 1, 5, chest2, chest_nbt('mnemolith:chests/hush_chapel'))
    b.set(7, 5, 5, lantern)
    # doorway to sanctuary
    b.fill(6, 1, 7, 8, 3, 7, air)
    # --- sanctuary / altar ---
    b.box(4, 0, 7, 10, 6, 10, mute_brick, air)
    b.fill(5, 0, 8, 9, 0, 9, mute)
    # altar dais
    b.fill(6, 1, 8, 8, 1, 9, mute_brick)
    b.set(7, 2, 9, shrine)
    b.set(7, 5, 9, lantern)
    b.set(5, 1, 9, chest, chest_nbt('mnemolith:chests/hush_chapel'))
    # side colonnade posts
    for x in (4, 10):
        b.fill(x, 1, 5, x, 4, 5, wall)
    b.write('hush_chapel.nbt')


def memory_field() -> None:
    """W2 plaza: open court + north pavilion + east lookout + west shrine alcove."""
    b = Builder(17, 6, 17)
    stratum = b.state('mnemolith:archival_stratum')
    bricks = b.state('mnemolith:archival_stratum_bricks')
    slab = b.state('mnemolith:archival_stratum_slab', {'type': 'bottom', 'waterlogged': 'false'})
    stairs = b.state('mnemolith:archival_stratum_stairs', {
        'facing': 'south', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'
    })
    air = b.air
    fence = b.state('minecraft:deepslate_brick_wall', {
        'east': 'none', 'north': 'none', 'south': 'none', 'up': 'true', 'waterlogged': 'false', 'west': 'none'
    })
    glass = b.state('minecraft:gray_stained_glass')
    chest = b.state('minecraft:chest', {'facing': 'west', 'type': 'single', 'waterlogged': 'false'})
    chest2 = b.state('minecraft:chest', {'facing': 'south', 'type': 'single', 'waterlogged': 'false'})
    shrine = b.state('mnemolith:archive_shrine', {'challenged': 'false', 'claimed': 'false'})
    b.fill(0, 0, 0, 16, 5, 16, air)
    # plaza floor
    b.fill(0, 0, 0, 16, 0, 16, bricks)
    # low perimeter wall
    for i in range(1, 16):
        b.set(i, 1, 1, fence)
        b.set(i, 1, 15, fence)
        b.set(1, 1, i, fence)
        b.set(15, 1, i, fence)
    # corner memory pillars
    for x, z in ((2, 2), (2, 14), (14, 2), (14, 14)):
        b.fill(x, 1, z, x, 4, z, stratum)
    # --- north pavilion (covered room) ---
    b.box(5, 0, 11, 11, 4, 15, bricks, air)
    b.fill(6, 0, 12, 10, 0, 14, bricks)
    b.fill(6, 4, 12, 10, 4, 14, slab)
    for x in (6, 10):
        for z in (12, 14):
            b.fill(x, 1, z, x, 3, z, stratum)
    b.set(8, 1, 13, chest, chest_nbt('mnemolith:chests/memory_field'))
    b.fill(7, 1, 11, 9, 2, 11, air)  # open to plaza
    # --- east lookout tower ---
    b.box(12, 0, 6, 15, 5, 10, bricks, air)
    b.fill(13, 0, 7, 14, 0, 9, bricks)
    b.fill(13, 1, 7, 14, 1, 9, stairs)
    b.fill(13, 2, 7, 14, 2, 9, slab)
    for x, z in ((13, 7), (13, 9), (14, 7), (14, 9)):
        b.set(x, 3, z, glass)
    b.set(14, 1, 8, chest2, chest_nbt('mnemolith:chests/memory_field'))
    b.fill(12, 1, 7, 12, 2, 9, air)
    # --- west shrine alcove ---
    b.box(1, 0, 6, 4, 4, 10, bricks, air)
    b.fill(2, 0, 7, 3, 0, 9, stratum)
    b.set(2, 1, 8, shrine)
    b.fill(4, 1, 7, 4, 2, 9, air)
    # central low platform
    b.fill(7, 1, 7, 9, 1, 9, bricks)
    b.fill(7, 2, 7, 9, 2, 9, slab)
    b.write('memory_field.nbt')


def ashen_archive() -> None:
    """W3 Nether: entry vestibule → magma corridor → shrine chamber."""
    b = Builder(15, 8, 13)
    black = b.state('minecraft:polished_blackstone_bricks')
    basalt = b.state('minecraft:smooth_basalt')
    magma = b.state('minecraft:magma_block')
    stratum = b.state('mnemolith:archival_stratum')
    air = b.air
    chest = b.state('minecraft:chest', {'facing': 'east', 'type': 'single', 'waterlogged': 'false'})
    chest2 = b.state('minecraft:chest', {'facing': 'north', 'type': 'single', 'waterlogged': 'false'})
    lantern = b.state('minecraft:soul_lantern', {'hanging': 'false', 'waterlogged': 'false'})
    shrine = b.state('mnemolith:archive_shrine', {'challenged': 'false', 'claimed': 'false'})
    b.fill(0, 0, 0, 14, 7, 12, air)
    # --- vestibule (west) ---
    b.box(0, 0, 3, 4, 6, 9, black, air)
    b.fill(1, 0, 4, 3, 0, 8, basalt)
    b.set(0, 1, 6, air)
    b.set(0, 2, 6, air)
    b.set(2, 1, 4, chest2, chest_nbt('mnemolith:chests/ashen_archive'))
    b.fill(4, 1, 5, 4, 3, 7, air)
    # --- magma corridor ---
    b.box(4, 0, 3, 9, 6, 9, black, air)
    b.fill(5, 0, 4, 8, 0, 8, basalt)
    for z in range(4, 9):
        b.set(5, 0, z, magma)
        b.set(8, 0, z, magma)
    for y in range(1, 5):
        b.set(5, y, 6, stratum)
        b.set(8, y, 6, stratum)
    b.fill(9, 1, 5, 9, 3, 7, air)
    # --- shrine chamber ---
    b.box(9, 0, 2, 14, 6, 10, black, air)
    b.fill(10, 0, 3, 13, 0, 9, basalt)
    # fire ring around shrine
    for x, z in ((11, 5), (12, 5), (11, 7), (12, 7), (10, 6), (13, 6)):
        b.set(x, 0, z, magma)
    b.set(11, 1, 6, shrine)
    b.set(12, 1, 6, lantern)
    b.set(11, 1, 4, chest, chest_nbt('mnemolith:chests/ashen_archive'))
    b.set(11, 5, 6, lantern)
    b.write('ashen_archive.nbt')


def mute_library() -> None:
    """W3 End: foyer → stack corridor → reading room."""
    b = Builder(15, 8, 13)
    end_brick = b.state('minecraft:end_stone_bricks')
    purpur = b.state('minecraft:purpur_block')
    pillar = b.state('minecraft:purpur_pillar', {'axis': 'y'})
    mute = b.state('mnemolith:mute_stone')
    shelf = b.state('minecraft:bookshelf')
    air = b.air
    chest = b.state('minecraft:chest', {'facing': 'south', 'type': 'single', 'waterlogged': 'false'})
    chest2 = b.state('minecraft:chest', {'facing': 'east', 'type': 'single', 'waterlogged': 'false'})
    lamp = b.state('minecraft:end_rod', {'facing': 'up'})
    b.fill(0, 0, 0, 14, 7, 12, air)
    # --- foyer (south) ---
    b.box(4, 0, 0, 10, 6, 3, end_brick, air)
    b.fill(5, 0, 1, 9, 0, 2, purpur)
    b.set(7, 1, 0, air)
    b.set(7, 2, 0, air)
    b.set(5, 1, 1, chest2, chest_nbt('mnemolith:chests/mute_library'))
    b.fill(6, 1, 3, 8, 3, 3, air)
    # --- stack corridor with mute+bookshelf aisles ---
    b.box(2, 0, 3, 12, 6, 8, end_brick, air)
    b.fill(3, 0, 4, 11, 0, 7, purpur)
    for x in (4, 7, 10):
        for z in range(4, 8):
            b.set(x, 1, z, mute)
            b.set(x, 2, z, shelf)
            b.set(x, 3, z, mute)
        # aisle gaps
    for x in (5, 6, 8, 9):
        b.fill(x, 1, 4, x, 3, 7, air)
    # clear central walk
    b.fill(6, 1, 4, 8, 3, 7, air)
    b.set(7, 1, 5, lamp)
    b.fill(6, 1, 8, 8, 3, 8, air)
    # --- reading room ---
    b.box(4, 0, 8, 10, 6, 12, end_brick, air)
    b.fill(5, 0, 9, 9, 0, 11, purpur)
    for x in (5, 9):
        b.fill(x, 1, 10, x, 4, 10, pillar)
    b.set(7, 1, 10, lamp)
    b.set(7, 1, 11, chest, chest_nbt('mnemolith:chests/mute_library'))
    b.write('mute_library.nbt')


def main() -> None:
    flooded_archive()
    hush_chapel()
    memory_field()
    ashen_archive()
    mute_library()


if __name__ == '__main__':
    main()
