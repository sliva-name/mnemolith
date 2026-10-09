# Memory Hollows, stage 2

Stage 1 ([memory-hollows.md](memory-hollows.md)) added the biome, its ground, recollite and the flickers. Stage 2
gives the hollows a reason to come back: a structure found only there, a way to keep what the flickers show, a
recollite upgrade for the lens, and the biome's own sound.

## Sunken archive

- **What.** A reading hall sunk into the turf (`data/mnemolith/structure/sunken_archive.nbt`, 17×14×17, built by
  `tools/build_w2_w3_structures.py`). Above ground only the hollowstone roof, fallen in over the middle, the stumps of
  the clerestory and a broken belfry over the doorway show. Inside: a landing, a stair down the west wall behind a
  parapet, two rows of bookshelves, an apse with a recollite plinth, a lectern and a chest, a second chest under the
  landing, cobwebs, soul lanterns, rubble and a forget-me-not under the roof hole.
- **No plain hollowstone in the template.** Hollowstone is in `#base_stone_overworld` and `#stone_ore_replaceables`
  (stage 1, so ores generate in it), and the ore features run after `surface_structures`. The first build used plain
  hollowstone for the floor, a few "cracked" wall blocks and the rubble; in game dirt and andesite blobs ate holes in
  the walls. The floor is brick now and the rubble is slabs; `hollows2qa` `template` fails if any block of the
  template is in either tag.
- **Placement.** Structure type `mnemolith:sunken_archive` (`MnemonicJigsawStructure.Kind.SUNKEN_ARCHIVE`): projected to
  `WORLD_SURFACE_WG` and then started 8 blocks lower, so template y=7 is ground level. The surface kinds keep a zero
  offset and behave as before. `terrain_adaptation: none` (the hall is meant to be buried; `beard_*` would dig it out).
- **Hollows only.** `biomes: #mnemolith:has_sunken_archive`, a tag with the single biome `mnemolith:memory_hollows`.
  The start is checked against the biome at the start point, so nothing generates outside the biome.
- **Spacing.** `random_spread`, spacing 8, separation 3 chunks, salt 61720433. Hollows cover about 1.6% of host land
  in patches a few hundred blocks wide, and a grid cell only yields an archive when its random start lands inside a
  patch. Measured on seed 424242 (12 patches, the nearest hollow to 12 points 3 000 to 6 000 blocks apart): with the
  first try, spacing 12 / separation 4, one patch in eight had an archive within 130 blocks; with 8 / 3, half of them
  do (6 of 12 within 100 blocks), and a large patch can hold two but never side by side. An archive stays a find,
  not a given.
- **Loot.** `chests/sunken_archive`: one roll of recollite shards (2–5), a chronicle lens, an extraction needle or the
  recollection disc, then 2–4 rolls of path, trade and player slips, shards, forget-me-nots, catalog fragments, books
  and paper.
- **Memory.** `imprint_seed/sunken_archive.json` seeds two path, trade or player imprints (intensity 2) in the archive's
  chunk, so flickers next to it replay something worth catching.
- **Config.** `worldGen.sunkenArchiveEnabled` (true, restart; also needs `structuresEnabled`).
- **Advancement.** *Under the Turf* (enter one), child of *Where Colour Wore Off*.

## Catching flickers

- **How.** Needle in the main hand, a chronicle lens in the off hand. Using the needle (in the air, or on a block:
  the flicker usually stands on the block you click) catches the nearest flicker still showing within reach: 5 blocks
  with a chronicle lens, 9 with a recollite lens. With no flicker in reach the needle extracts as before.
- **Server side.** `HollowFlickers` keeps each sent flicker for `CATCH_WINDOW` = 90 ticks (the client draws it for
  80) with its spot and tag ordinal, at most 256. `tryCatch` picks the nearest one in reach and calls
  `ImprintWriter.catchFlicker`, which takes the strongest imprint of that tag out of the flicker's chunk (marks the
  chunk archival and recomputes pressure, as extraction does) and hands over a slip. The flicker is removed, so it is
  caught once; a `HollowFlickerPayload` with scene `CAUGHT` (-1) tells nearby clients to fold it into a burst of
  shimmer.
- **Refusals.** A flicker of a chunk with no imprint is *too faint* (nothing to hold). If the imprint has already
  left the chunk, or the slip does not fit, the flicker *slips away* (the full-inventory message is the usual one).
  No flicker in reach: a short message when used in the air.
- **Cost.** Same as an extraction: the needle cooldown and durability (reinforced and twin needles take no
  durability). Catching does not double: a twin needle catches one flicker.
- **Why the imprint leaves the chunk.** Otherwise a hollow would be an endless slip fountain. Catching is a way to see
  what a chunk holds and pick that tag out from range, not a new source.
- **Advancement.** *Hold That Thought* (catch one), awarded in code (`minecraft:impossible` criterion).

## Recollite lens

- `mnemolith:recollite_lens`, a `ChronicleLensItem` subclass, so it reads pressure, focuses, tunes to a slip and
  blinds replicants exactly like the chronicle lens (`isHeld`, `isFocusing`, `heldFilter` and the tooltip now test
  `instanceof ChronicleLensItem`).
- In Memory Hollows: flicker chance ×1.5 around its holder, catch reach 9 instead of 5.
- Recipe: the chronicle lens in the middle, three recollite shards above and at the sides, a recollite block below
  (12 shards' worth: a mid-game goal after a few hollows).
- Sprite: the chronicle lens drawn with recollite glass and three recollite chips in the rim
  (`tools/art/mnart/hollows.py`, `recollite_lens`; `items.chronicle_lens` takes the glass ramp as a parameter, and
  its default output is pixel-identical). Flat in the hand (`item/handheld`).
- Advancement *Sharper Recollection*, recipe unlocked on picking up a shard.

## Early progression: amethyst still works

Stage 1 changed every Mnemolith recipe that took `minecraft:amethyst_shard` to take `#mnemolith:memory_crystals`.
That tag holds **both** the amethyst shard and the recollite shard, so nothing became recollite-only: the chronicle
lens, the extraction needle and the field guide (all needed before anyone has found a hollow) are still made with an
amethyst shard. Only the recollite lens and the recollite block need recollite. `hollowsqa` `crystalTag` checks that
every ingredient that takes amethyst also takes the shard; `hollows2qa` `earlyAmethyst` checks the other direction
for the lens, needle and guide (they take amethyst and none of their slots is recollite-only). No separate amethyst
recipes are needed.

## Sound

- All four sounds are synthesised by `tools/art/generate_sounds.py --hollows` from sine/noise math: no samples, no
  third-party or copyrighted audio. Same style as the existing placeholder music (soft pads, simple tones).
- `music.memory_hollows`: a 48 s pad on D with slow glass-bell phrases (D dorian), streamed. Biome attribute
  `minecraft:audio/background_music` (default, 3 600–12 000 ticks between plays, does not cut off playing music).
- `ambient.memory_hollows.additions`: one far glass chime at four pitches, `tick_chance` 0.004 (about one every 12 s).
- `ambient.memory_hollows.mood`: a low breath, the cave-mood slot (6 000-tick delay, like vanilla).
- `flicker_catch`: two rising bells and a shimmer, played to the catcher.
- Subtitles for all but the music.

## Field guide

- New page `sunken` after `hollows` (38 pages): the archive's roof and belfry in the turf with a flicker, lens + needle
  → slip, and the two lens reaches. The `hollows` page text now points to it.

## Tests

- `/mnemolith hollows2qa` (also a GameTest suite, 18 checks): the structure is registered and its biome set is exactly
  Memory Hollows; the structure set is an 8/3 random spread; the template has its two chests and no
  ore-replaceable block; the loot table rolls items every time and shards in most rolls; the needle's own `use` with a lens in the off hand catches a path
  flicker into one path slip and takes the imprint out of the chunk (the trade imprint stays); the same flicker
  cannot be caught twice; a tagless flicker is faint; a flicker whose imprint is gone slips away; at 7 blocks a
  chronicle lens misses and a recollite lens catches; without a lens nothing is caught; the recollite lens recipe
  matches and the lens counts as a chronicle lens; the early recipes take amethyst; the four sounds are registered
  and the biome has its music and ambience; the advancements load; the guide page sits after `hollows`.
- Live GameTest `hollows_flicker_delivered_and_caught` (stage 1 deferred this): three real server players on
  in-memory connections. The one standing in Memory Hollows (made with `fillbiome`) is offered a flicker; the
  `HollowFlickerPayload` read off its connection replays the chunk's path imprint 6–20 blocks away; a friend 6 blocks
  off receives the same payload and a player 144 blocks off receives none. Then the first player catches it with
  needle and lens through `gameMode.useItem`, gets one path slip and the advancement, and both nearby players get the
  `CAUGHT` payload.
- `structure_templates_place` walks the sunken archive with a multi-level flood fill (steps up and down one block,
  stairs and slabs count as ground) from ground level to both chests.
- Manual: `/locate structure mnemolith:sunken_archive` on a fresh normal world, screenshots of the archive, catching a
  flicker in game, the guide page in RU and EN. `/mnemolith hollows flicker` now carries an imprint of the chunk it
  lands in, so a debug flicker can be caught too.

## Next stages

- Stage 3 ideas: a hollows mob (a "faded" that copies the last flicker), recollite tools, a lectern in the archive
  that replays a stored flicker on demand, hollows-specific imprint tags.
