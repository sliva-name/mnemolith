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
    def __init__(self, sx: int, sy: int, sz: int, ox: int = 0, oy: int = 0, oz: int = 0):
        self.sx, self.sy, self.sz = sx, sy, sz
        # Offset added to every coordinate: room for a foundation below y=0 or a landing in front of a door.
        self.ox, self.oy, self.oz = ox, oy, oz
        # Doorways between rooms, carved at write time. Rooms share their walls, so a doorway carved before the next
        # room's box was built used to be walled up again, and the main halls were sealed off from their entrances.
        self.openings: list[tuple[int, int, int, int, int, int]] = []
        self.palette_index: OrderedDict[tuple, int] = OrderedDict()
        self.palette: list[bytes] = []
        # One entry per position, last write wins. A template with two entries for one position places them in
        # Minecraft's order (full blocks, then other blocks such as air, then block entities), so a later "clear to
        # air" pass used to erase the walls and leave only chests, walls and lanterns standing.
        self.cells: OrderedDict[tuple[int, int, int], tuple[int, bytes | None]] = OrderedDict()
        self.air = self.state('minecraft:air')

    def state(self, name: str, props: dict[str, str] | None = None) -> int:
        key = (name, tuple(sorted((props or {}).items())))
        if key not in self.palette_index:
            self.palette_index[key] = len(self.palette)
            self.palette.append(block_state(name, props))
        return self.palette_index[key]

    def set(self, x: int, y: int, z: int, state: int, nbt: bytes | None = None) -> None:
        x, y, z = x + self.ox, y + self.oy, z + self.oz
        if not (0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz):
            return
        self.cells[(x, y, z)] = (state, nbt)

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

    def opening(self, x0, y0, z0, x1, y1, z1) -> None:
        """A doorway or passage, carved to air after every room is built (see ``openings``)."""
        self.openings.append((x0, y0, z0, x1, y1, z1))

    def write(self, rel: str) -> None:
        for x0, y0, z0, x1, y1, z1 in self.openings:
            self.fill(x0, y0, z0, x1, y1, z1, self.air)
        blocks = [block_entry(state, pos, nbt) for pos, (state, nbt) in self.cells.items()]
        write_structure(os.path.join(OUT, rel), (self.sx, self.sy, self.sz), self.palette, blocks)


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
    b.opening(0, 1, 6, 0, 2, 6)
    # --- corridor / doorway into flooded hall ---
    b.opening(5, 1, 5, 5, 3, 7)
    # --- room 2: flooded hall (center) ---
    b.box(5, 0, 0, 11, 6, 12, deep, air)
    b.fill(6, 0, 1, 10, 0, 11, tiles)
    b.fill(6, 1, 1, 10, 1, 11, water)
    # dry walkways along the north and south walls, a stratum kerb between them and the water channel
    b.fill(6, 1, 1, 10, 1, 2, tiles)
    b.fill(6, 1, 10, 10, 1, 11, tiles)
    for x in range(6, 11):
        b.set(x, 1, 3, stratum)
        b.set(x, 1, 9, stratum)
    # pillars
    for z in (3, 9):
        b.fill(8, 1, z, 8, 4, z, bricks)
    # doorway into vault room
    b.opening(11, 1, 5, 11, 3, 7)
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
    b.opening(7, 1, 0, 7, 2, 0)
    # doorway to nave
    b.opening(6, 1, 3, 8, 3, 3)
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
    b.opening(6, 1, 7, 8, 3, 7)
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
    b.opening(7, 1, 11, 9, 2, 11)  # open to plaza
    # --- east lookout tower ---
    b.box(12, 0, 6, 15, 5, 10, bricks, air)
    b.fill(13, 0, 7, 14, 0, 9, bricks)
    # windows in the outer walls (the room used to be filled with stairs and slabs that buried its chest)
    for x, z in ((15, 7), (15, 9), (13, 6), (14, 10)):
        b.set(x, 3, z, glass)
    b.set(14, 1, 7, stairs)
    b.set(14, 1, 8, chest2, chest_nbt('mnemolith:chests/memory_field'))
    b.opening(12, 1, 7, 12, 2, 9)
    # --- west shrine alcove ---
    b.box(1, 0, 6, 4, 4, 10, bricks, air)
    b.fill(2, 0, 7, 3, 0, 9, stratum)
    b.set(2, 1, 8, shrine)
    b.opening(4, 1, 7, 4, 2, 9)
    # central low platform
    b.fill(7, 1, 7, 9, 1, 9, bricks)
    b.fill(7, 2, 7, 9, 2, 9, slab)
    b.write('memory_field.nbt')


ASHEN_FOUNDATION = 8


def ashen_archive() -> None:
    """W3 Nether: entry vestibule → magma corridor → shrine chamber, on basalt piers over the lava sea.

    The floor sits ASHEN_FOUNDATION blocks above the template's bottom (MnemonicJigsawStructure places the template
    that much lower, so the floor stays at y=32, just over the lava sea), with a landing in front of the west door.
    """
    b = Builder(18, 8 + ASHEN_FOUNDATION, 15, ox=3, oy=ASHEN_FOUNDATION, oz=1)
    black = b.state('minecraft:polished_blackstone_bricks')
    pier = b.state('minecraft:basalt', {'axis': 'y'})
    footing = b.state('minecraft:polished_blackstone')
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
    b.opening(0, 1, 6, 0, 2, 6)
    b.set(2, 1, 4, chest2, chest_nbt('mnemolith:chests/ashen_archive'))
    b.opening(4, 1, 5, 4, 3, 7)
    # --- magma corridor ---
    b.box(4, 0, 3, 9, 6, 9, black, air)
    b.fill(5, 0, 4, 8, 0, 8, basalt)
    for z in range(4, 9):
        b.set(5, 0, z, magma)
        b.set(8, 0, z, magma)
    for y in range(1, 5):
        b.set(5, y, 6, stratum)
        b.set(8, y, 6, stratum)
    b.opening(9, 1, 5, 9, 3, 7)
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
    # --- foundation: a blackstone footing under the floor and basalt piers down into the lava sea ---
    for x0, z0, x1, z1 in ((0, 3, 4, 9), (4, 3, 9, 9), (9, 2, 14, 10)):
        b.fill(x0, -1, z0, x1, -1, z1, footing)
    for x, z in ((0, 3), (0, 9), (4, 3), (4, 9), (9, 2), (9, 10), (14, 2), (14, 10), (14, 6), (7, 3), (7, 9), (-3, 5), (-3, 7)):
        b.fill(x, -ASHEN_FOUNDATION, z, x, -2, z, pier)
    # landing in front of the west door, so it no longer opens straight onto the lava
    b.fill(-3, 0, 5, -1, 0, 7, black)
    b.fill(-3, 1, 5, -1, 3, 7, air)
    b.fill(-3, -1, 5, -1, -1, 7, footing)
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
    b.opening(7, 1, 0, 7, 2, 0)
    b.set(5, 1, 1, chest2, chest_nbt('mnemolith:chests/mute_library'))
    b.opening(6, 1, 3, 8, 3, 3)
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
    b.opening(6, 1, 8, 8, 3, 8)
    # --- reading room ---
    b.box(4, 0, 8, 10, 6, 12, end_brick, air)
    b.fill(5, 0, 9, 9, 0, 11, purpur)
    for x in (5, 9):
        b.fill(x, 1, 10, x, 4, 10, pillar)
    b.set(7, 1, 10, lamp)
    b.set(7, 1, 11, chest, chest_nbt('mnemolith:chests/mute_library'))
    b.write('mute_library.nbt')


def sunken_archive() -> None:
    """Memory Hollows: a reading hall sunk into the turf. The template starts 8 blocks under the surface
    (MnemonicJigsawStructure.Kind.SUNKEN_ARCHIVE), so y=7 is ground level: the roof and a broken belfry stand above
    the turf, a doorway at y=8 opens onto a landing and a stair runs down the west wall to the hall floor."""
    b = Builder(17, 14, 17)
    bricks = b.state('mnemolith:hollowstone_bricks')
    wall = b.state('mnemolith:hollowstone_brick_wall', {
        'east': 'none', 'north': 'none', 'south': 'none', 'west': 'none', 'up': 'true', 'waterlogged': 'false'})
    slab = b.state('mnemolith:hollowstone_brick_slab', {'type': 'bottom', 'waterlogged': 'false'})
    recollite = b.state('mnemolith:recollite_block')
    shelf = b.state('minecraft:bookshelf')
    web = b.state('minecraft:cobweb')
    air = b.air
    stairs_n = b.state('mnemolith:hollowstone_brick_stairs', {
        'facing': 'north', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
    lectern = b.state('minecraft:lectern', {'facing': 'west', 'has_book': 'false', 'powered': 'false'})
    chest_w = b.state('minecraft:chest', {'facing': 'west', 'type': 'single', 'waterlogged': 'false'})
    chest_n = b.state('minecraft:chest', {'facing': 'north', 'type': 'single', 'waterlogged': 'false'})
    lantern = b.state('minecraft:soul_lantern', {'hanging': 'true', 'waterlogged': 'false'})
    flower = b.state('mnemolith:forget_me_not')
    loot = 'mnemolith:chests/sunken_archive'

    # hall: walls y0..10, roof at y10, interior x1..15 z3..13 y1..9
    b.box(0, 0, 2, 16, 10, 14, bricks, air)
    # no plain hollowstone anywhere: it is in #base_stone_overworld, so the ore features that run after
    # surface_structures would eat it (dirt, gravel and andesite blobs turned up in the walls and floor)
    # doorway in the north wall at ground level, a landing and a stair down the west wall
    b.fill(1, 8, 0, 2, 10, 1, air)
    b.opening(1, 8, 2, 2, 9, 2)
    b.fill(1, 7, 0, 2, 7, 1, bricks)
    b.fill(0, 7, 0, 0, 9, 1, bricks)
    b.fill(3, 7, 0, 3, 9, 1, bricks)
    b.fill(1, 7, 3, 2, 7, 3, bricks)
    for i in range(7):
        z, y = 4 + i, 7 - i
        b.fill(1, y, z, 2, y, z, stairs_n)
        if y > 1:
            b.fill(1, 1, z, 2, y - 1, z, bricks)
    # the stair is walled off from the hall by a low parapet
    for z in range(4, 11):
        b.set(3, 8 - (z - 4), z, wall)
    # two shelf rows with a walk between them
    for z in (6, 10):
        for x in range(5, 14):
            if x == 9:
                continue
            for y in (1, 2, 3):
                b.set(x, y, z, shelf)
        b.set(4, 1, z, wall)
        b.set(4, 2, z, wall)
        b.set(4, 3, z, wall)
    # east apse: recollite plinth, lectern and the main chest on a raised floor
    b.fill(13, 1, 7, 15, 1, 9, bricks)
    b.fill(12, 1, 7, 12, 1, 9, slab)
    b.set(15, 1, 8, recollite)
    b.set(14, 2, 8, lectern)
    b.set(15, 2, 7, chest_w, chest_nbt(loot))
    # a second chest tucked under the stair landing
    b.set(4, 1, 12, chest_n, chest_nbt(loot))
    # the roof fell in over the middle: a hole open to the sky and its rubble on the floor
    for (x, z) in ((8, 8), (9, 8), (10, 8), (8, 9), (9, 9), (10, 9), (9, 7), (11, 9), (9, 10)):
        b.set(x, 10, z, air)
    for (x, z) in ((9, 8), (10, 9), (8, 9)):
        b.set(x, 1, z, slab)
    b.set(9, 1, 8, bricks)
    b.set(9, 2, 8, slab)
    b.set(9, 1, 9, flower)
    for (x, y, z) in ((1, 9, 13), (15, 9, 3), (14, 9, 13), (6, 9, 4), (12, 4, 11)):
        b.set(x, y, z, web)
    for (x, z) in ((5, 8), (12, 4), (12, 12)):
        b.set(x, 9, z, lantern)
    # above the turf: a broken belfry over the doorway and stumps of the old clerestory
    b.box(0, 10, 0, 3, 13, 3, bricks, air)
    b.fill(1, 10, 1, 2, 12, 2, air)
    b.fill(1, 10, 0, 2, 11, 0, air)
    b.set(3, 13, 3, air)
    b.set(0, 13, 3, air)
    b.set(3, 13, 0, air)
    for (x, z, h) in ((8, 2, 12), (16, 8, 12), (8, 14, 11), (16, 14, 11), (16, 2, 13)):
        b.fill(x, 11, z, x, h, z, wall)
    b.write('sunken_archive.nbt')


def main() -> None:
    flooded_archive()
    hush_chapel()
    memory_field()
    ashen_archive()
    mute_library()
    sunken_archive()


if __name__ == '__main__':
    main()
