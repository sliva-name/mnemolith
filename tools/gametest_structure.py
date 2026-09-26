#!/usr/bin/env python3
"""Writes data/mnemolith/structure/gametest/empty.nbt: a 3x3x3 air template, the anchor every Mnemolith game test
stands on. The QA suites build their own sites in far chunks, so the template only needs to exist.
Run from the repo root: python3 tools/gametest_structure.py"""
import gzip, struct

def tag_string(s):
    b = s.encode('utf-8'); return struct.pack('>H', len(b)) + b

def named(tid, name, payload):
    return bytes([tid]) + tag_string(name) + payload

def int_list(values):  # TAG_List of TAG_Int
    return bytes([3]) + struct.pack('>i', len(values)) + b''.join(struct.pack('>i', v) for v in values)

def compound_list(items):  # TAG_List of TAG_Compound; each item is already a sequence of named tags
    return bytes([10 if items else 0]) + struct.pack('>i', len(items)) + b''.join(i + b'\x00' for i in items)

root = b''.join([
    named(3, 'DataVersion', struct.pack('>i', 4903)),
    named(9, 'size', int_list([3, 3, 3])),
    named(9, 'palette', compound_list([named(8, 'Name', tag_string('minecraft:air'))])),
    named(9, 'blocks', compound_list([])),
    named(9, 'entities', compound_list([])),
])
data = named(10, '', root + b'\x00')
with open('src/main/resources/data/mnemolith/structure/gametest/empty.nbt', 'wb') as f:
    f.write(gzip.compress(data, mtime=0))
print('wrote empty.nbt', len(data), 'bytes raw')
