# The recollite pickaxe

Status: implemented on `feature/recollite-pickaxe`. It comes from the "Next stages" list in
[memory-hollows-3.md](memory-hollows-3.md) and [memory-hollows-4.md](memory-hollows-4.md). It applies the G1 idea in
[expansion-ideas.md](expansion-ideas.md) ("the weapon reads the chunk tag", used by the Recall Blade) to mining.

## Goal

The recollite lens and needle let you *see* and *catch* memories. The pickaxe is the third recollite tool, and it lets
you *dig* through them: you learn what a chunk remembers just by working in it, and the memory changes the work a
little. It reads, it does not write: the chunk's memory is left alone, except that a silent chunk stays quiet.

## Item

- `mnemolith:recollite_pickaxe` is an iron-tier pickaxe (`RecollitePickaxeItem.MATERIAL`): mining level and speed 6.5
  like iron, 500 uses (iron has 250), enchantability 16. It is repaired with recollite shards
  (`mnemolith:repairs_recollite_pickaxe`) and is in `minecraft:pickaxes`, so it takes pickaxe enchantments.
- Recipe: shard, iron ingot, shard over two sticks. The recipe unlocks once you have a recollite shard. Advancement
  *Reading the Rock* (parent *recollite*).
- Hint tooltip, a creative tab entry, and the field guide page `pickaxe` (after `forgotten`; `PAGE_COUNT` 41).

## Reading (`PickaxeReading`)

Every block broken with the pickaxe in the main hand is read against the **loudest imprint** of its chunk
(`ChunkMemory.highest()`). A full or selective mute stone in the chunk hushes the reading, which then counts as "nothing".

Effects on every block (from `BlockDropsEvent` and `ImprintEvents.onBreak`):

| Loudest tag | Effect |
| --- | --- |
| fire | the drops come out smelted (same smelting lookup as the kindled echo) |
| portal | the drops land at the miner's feet with no pickup delay |
| death | a block that already drops experience drops 3 more (ores, not dirt) |
| silence, forgotten | the break writes no build imprint |

At most once every `READ_COOLDOWN` (60 ticks) per player:

- a line over the hotbar: "The stone remembers: Fire" or "The stone remembers nothing.";
- a short effect (5 s): haste I for build, path, player, trade and redstone; haste II for boss and explosion; slow
  falling for fall; night vision (10 s) for sculk; lightning makes monsters within 16 blocks glow;
- **in Memory Hollows** the memory surfaces as a flicker at the broken block (`HollowFlickers.send`, the tag's own
  scene). It can be caught with a needle like any other. If it is left uncaught it can be forgotten
  ([memory-hollows-4.md](memory-hollows-4.md)), so digging carelessly in a hollow wears its memories out. As with
  every flicker, a faded nearby may come to copy it.

Why these effects: they stay small (no extra drops, no block damage, no griefing), they match the Recall Blade's
table in spirit, and the per-block ones only matter where a memory really is loud. Imprints fade and can be extracted,
so a fire chunk does not smelt forever. Reading is cheap: one chunk-memory lookup per block, with no scans except
the lightning reveal, which runs once per cooldown.

## QA

- `/mnemolith pickaxeqa` and the `mnemolith:suite_pickaxeqa` game test (14 checks): item data, recipe and
  advancements, reading nothing and the loudest tag, mute hushing, fire smelting, portal pulling, death experience,
  silence and forgotten quiet (not with an iron pickaxe, not in a path chunk), effects, the cooldown, a flicker in a
  hollow and none on plains, and the guide page.
- Live: `pickaxe_reads_fire_in_hollow` (a real player breaks iron ore in a fire chunk in a hollow: an ingot, a worn
  pickaxe, a fire flicker on the connection) and `pickaxe_quiet_in_silent_chunk` (silence: no build imprint; a path
  chunk next to it: one).
- Manual, still to do on a normal world: the hotbar line and its colour, the pickaxe texture in hand, and a flicker
  rising out of a dug block in a real hollow.

## Not included

- No mining speed bonus from pressure, and no reading of the imprints themselves (count, age, owner): the lens does
  that.
- The echo mining job does not read with a recollite pickaxe; echoes keep their own graft system.
