#!/usr/bin/env python3
"""Build W2/W3 structure NBTs under data/mnemolith/structure/.

Run from repo root: python3 tools/build_w2_w3_structures.py
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
    # TAG_List of TAG_Compound; each item is named-tag sequence without the outer compound id
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
    print('wrote', path, 'blocks', len(blocks), 'raw', len(data))


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
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
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

    def write(self, rel: str) -> None:
        write_structure(os.path.join(OUT, rel), (self.sx, self.sy, self.sz), self.palette, self.blocks)


def flooded_archive() -> None:
    b = Builder(11, 7, 11)
    deep = b.state('minecraft:deepslate_bricks')
    stratum = b.state('mnemolith:archival_stratum')
    water = b.state('minecraft:water', {'level': '0'})
    air = b.air
    chest = b.state('minecraft:chest', {'facing': 'south', 'type': 'single', 'waterlogged': 'false'})
    vault = b.state('mnemolith:archive_vault')
    # hollow underground chamber
    b.box(0, 0, 0, 10, 6, 10, deep, air)
    # archival veins along walls
    for y in range(1, 5):
        for z in range(2, 9):
            b.set(1, y, z, stratum)
            b.set(9, y, z, stratum)
        for x in range(2, 9):
            b.set(x, y, 1, stratum)
            b.set(x, y, 9, stratum)
    # flooded floor
    b.fill(2, 1, 2, 8, 1, 8, water)
    # dry ledge with vault + chest
    b.fill(3, 1, 3, 5, 1, 5, deep)
    b.set(4, 2, 4, vault)
    shrine = b.state('mnemolith:archive_shrine', {'challenged': 'false', 'claimed': 'false'})
    b.set(4, 2, 5, shrine)
    b.set(5, 2, 4, chest, chest_nbt('mnemolith:chests/flooded_archive'))
    # doorway carved
    b.set(10, 2, 5, air)
    b.set(10, 3, 5, air)
    b.write('flooded_archive.nbt')


def hush_chapel() -> None:
    b = Builder(9, 6, 9)
    mute = b.state('mnemolith:mute_stone')
    mute_brick = b.state('mnemolith:mute_stone_bricks')
    air = b.air
    chest = b.state('minecraft:chest', {'facing': 'north', 'type': 'single', 'waterlogged': 'false'})
    lantern = b.state('minecraft:soul_lantern', {'hanging': 'true', 'waterlogged': 'false'})
    b.box(0, 0, 0, 8, 5, 8, mute_brick, air)
    # mute lining
    for x in range(1, 8):
        for z in range(1, 8):
            b.set(x, 0, z, mute)
    for y in range(1, 4):
        for z in range(1, 8):
            b.set(1, y, z, mute)
            b.set(7, y, z, mute)
        for x in range(1, 8):
            b.set(x, y, 1, mute)
            b.set(x, y, 7, mute)
    # doorway
    b.set(4, 1, 0, air)
    b.set(4, 2, 0, air)
    b.set(4, 1, 1, air)
    b.set(4, 2, 1, air)
    # interior — archive shrine at the altar (B4 guardian challenge)
    shrine = b.state('mnemolith:archive_shrine', {'challenged': 'false', 'claimed': 'false'})
    b.set(4, 1, 4, shrine)
    b.set(4, 4, 4, lantern)
    b.set(4, 1, 6, chest, chest_nbt('mnemolith:chests/hush_chapel'))
    b.write('hush_chapel.nbt')


def memory_field() -> None:
    b = Builder(13, 5, 13)
    stratum = b.state('mnemolith:archival_stratum')
    bricks = b.state('mnemolith:archival_stratum_bricks')
    air = b.air
    fence = b.state('minecraft:deepslate_brick_wall', {
        'east': 'none', 'north': 'none', 'south': 'none', 'up': 'true', 'waterlogged': 'false', 'west': 'none'
    })
    glass = b.state('minecraft:gray_stained_glass')
    chest = b.state('minecraft:chest', {'facing': 'west', 'type': 'single', 'waterlogged': 'false'})
    slab = b.state('mnemolith:archival_stratum_slab', {'type': 'bottom', 'waterlogged': 'false'})
    # plaza floor
    b.fill(0, 0, 0, 12, 0, 12, bricks)
    # clear air column
    b.fill(0, 1, 0, 12, 4, 12, air)
    # four memory pillars
    for x, z in ((2, 2), (2, 10), (10, 2), (10, 10)):
        b.fill(x, 1, z, x, 3, z, stratum)
    # ring of low walls / fence posts (stalker-visible plaza)
    for i in range(1, 12):
        b.set(i, 1, 1, fence)
        b.set(i, 1, 11, fence)
        b.set(1, 1, i, fence)
        b.set(11, 1, i, fence)
    # elevated lookout (safe vantage, vanilla glass — not scar ward)
    b.fill(5, 1, 5, 7, 1, 7, bricks)
    b.fill(5, 2, 5, 7, 2, 7, slab)
    for x, z in ((5, 5), (5, 7), (7, 5), (7, 7)):
        b.set(x, 3, z, glass)
    b.set(6, 2, 6, chest, chest_nbt('mnemolith:chests/memory_field'))
    b.write('memory_field.nbt')


def ashen_archive() -> None:
    b = Builder(11, 7, 11)
    black = b.state('minecraft:polished_blackstone_bricks')
    basalt = b.state('minecraft:smooth_basalt')
    magma = b.state('minecraft:magma_block')
    stratum = b.state('mnemolith:archival_stratum')
    air = b.air
    chest = b.state('minecraft:chest', {'facing': 'east', 'type': 'single', 'waterlogged': 'false'})
    lantern = b.state('minecraft:soul_lantern', {'hanging': 'false', 'waterlogged': 'false'})
    b.box(0, 0, 0, 10, 6, 10, black, air)
    b.fill(1, 0, 1, 9, 0, 9, basalt)
    # fire veins
    for z in range(2, 9):
        b.set(2, 0, z, magma)
        b.set(8, 0, z, magma)
    for y in range(1, 5):
        b.set(1, y, 5, stratum)
        b.set(9, y, 5, stratum)
    shrine = b.state('mnemolith:archive_shrine', {'challenged': 'false', 'claimed': 'false'})
    b.set(5, 1, 5, shrine)
    b.set(5, 2, 5, lantern)
    b.set(3, 1, 5, chest, chest_nbt('mnemolith:chests/ashen_archive'))
    # entrance
    b.set(0, 1, 5, air)
    b.set(0, 2, 5, air)
    b.write('ashen_archive.nbt')


def mute_library() -> None:
    b = Builder(11, 7, 11)
    end_brick = b.state('minecraft:end_stone_bricks')
    purpur = b.set if False else b.state('minecraft:purpur_block')
    mute = b.state('mnemolith:mute_stone')
    shelf = b.state('minecraft:bookshelf')
    air = b.air
    chest = b.state('minecraft:chest', {'facing': 'south', 'type': 'single', 'waterlogged': 'false'})
    lamp = b.state('minecraft:end_rod', {'facing': 'up'})
    b.box(0, 0, 0, 10, 6, 10, end_brick, air)
    b.fill(1, 0, 1, 9, 0, 9, purpur)
    # mute shelves
    for x in (2, 5, 8):
        for z in range(2, 9):
            b.set(x, 1, z, mute)
            b.set(x, 2, z, shelf)
            b.set(x, 3, z, mute)
    b.set(5, 1, 5, lamp)
    b.set(5, 1, 8, chest, chest_nbt('mnemolith:chests/mute_library'))
    b.set(5, 1, 0, air)
    b.set(5, 2, 0, air)
    b.write('mute_library.nbt')


def main() -> None:
    flooded_archive()
    hush_chapel()
    memory_field()
    ashen_archive()
    mute_library()


if __name__ == '__main__':
    main()
