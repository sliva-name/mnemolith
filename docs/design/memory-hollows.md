# Memory Hollows, stage 1: biome, terrain, flickers, ore

Status: stage 1 implemented on `feature/memory-hollows`. The first increment outside echo combat after three guard stages.

## Problem

Every Mnemolith system lives in the vanilla world: imprints are written into ordinary stone, structures sit in
ordinary biomes, and amethyst is the one memory crystal the recipes know. There is no place where the world itself
looks like memory, and nothing to find by travelling. The roadmap and [expansion-ideas.md](expansion-ideas.md) list
"a new biome" under world and presentation, and it has never been built.

## Goals

- A rare Overworld biome, **Memory Hollows** (Лощины памяти), that reads at a glance: pale turf and lavender grass,
  pale stone under the surface, broken remnant pillars, small sunken hollows, forget-me-nots.
- **Memory flickers:** short ghostly replays of people who were here, built from the imprints the chunk already holds.
- A new ore (**recollite**) with a material use, a small building family and a flower.
- No change to vanilla terrain shape, no replaced dimension or noise settings, no hard dependency, so other worldgen
  mods keep working.
- Recipes, advancements, JEI, field guide page, en_us and ru_ru, config, QA suite and GameTests, as usual.

## Non-goals (stage 1)

- New terrain shape (the noise router is untouched on purpose), structures unique to the biome, new mobs.
- Recollite tools or armour, flicker interaction (catching or reading a flicker), music.

## Placement: a region layer over the vanilla biome source

NeoForge 26.2 has no biome-placement API, and the overworld biome list is the hard-coded `minecraft:overworld`
preset. Overriding the dimension or the preset by datapack would replace every mod's and every pack's overworld. So
the biome is placed by a **region**: a small replacement layer over the multi-noise biome source.

- **Datapack-driven.** The biome is `data/mnemolith/worldgen/biome/memory_hollows.json`. The region is a NeoForge
  biome modifier of a new type, `mnemolith:biome_region`
  (`data/mnemolith/neoforge/biome_modifier/memory_hollows_region.json`): the biome to place, the host biomes (the tag
  `#mnemolith:memory_hollows_hosts`: plains, sunflower plains, meadow, forest, flower forest, birch forests, taiga, old
  growth taigas, dark forest), and a climate window (optional `temperature`, `humidity`, `continentalness`, `erosion`, `weirdness` ranges; a missing
  one spans everything). The shipped window is `weirdness` 0.08..0.26, `erosion` 0.2..0.4, `continentalness` 0.0..0.5
  (inland, so it rarely lands on a coast).
  A pack can retune the window or the hosts, or turn the region off by overriding the file with `neoforge:none`.
- **How it applies.** NeoForge applies biome modifiers when a server starts, before it rebuilds the per-step feature
  lists. Applying our modifier registers the region; it changes nothing in the biome it is applied to.
- **Mixin, two hooks** (`MultiNoiseBiomeSourceMixin`, `BiomeSourceMixin`):
  - `MultiNoiseBiomeSource.getNoiseBiome(TargetPoint)`: when vanilla picks a host biome and the climate point lies
    inside the window, return Memory Hollows instead. Anything else, including other mods' biomes, is untouched.
  - `BiomeSource.possibleBiomes()`: a multi-noise source that can produce a host also lists Memory Hollows, so its
    features are indexed, structures can see it and `/locate biome` searches for it. The set is cached per source.
- **Why climate and not a seeded noise.** The target point already carries five smooth, seeded noises; a window over
  them gives coherent patches with soft edges that follow the land, at no extra sampling cost. Weirdness and erosion
  are the ones that vary inside one host biome. Terrain height comes from the noise router, not the biome, so the
  ground does not change shape at the border.
- **Rarity.** Target: 1 to 2 % of the host area. Measured with `/mnemolith hollows survey 4096` on seed 424242: the
  first guess (weirdness 0.05..0.3, erosion 0.1..0.45) covered 7.4 % of host land; the shipped window covers 1.6 %
  (0.7 % of all samples), patches of roughly 80 × 60 blocks, the nearest 706 blocks from spawn.
- **Off switch.** `memoryHollowsEnabled` (common config, restart) stops the region from registering. Chunks already
  generated keep their biome.

## Terrain (datapack features, Java feature types)

| Step | Feature | What it does |
| --- | --- | --- |
| `local_modifications` | `mnemolith:hollow_sink` | 1 in 5 chunks: a shallow dish (radius 3 to 6, depth 1 to 3) on dry, level ground. |
| `surface_structures` | `mnemolith:hollow_remnant` | 1 in 3 chunks: a broken hollowstone pillar or arch stub, 3 to 7 high, sometimes with a recollite block showing. |
| `vegetal_decoration` | vanilla trees and grass, `mnemolith:patch_forget_me_not` | Sparse birch and oak, lavender-tinted grass, forget-me-not patches. |
| `top_layer_modification` | `mnemolith:hollow_ground` | Every column whose surface is in the biome: grass block to **hollow turf**; stone, andesite, diorite, granite and tuff from 3 to 18 blocks under the surface to **hollowstone**; then a few small **recollite ore** clusters inside that layer. |

The ground pass reads one biome per column and writes only inside its own chunk. It is the only feature that touches
every column, and it runs once per chunk at generation.

The biome also gets every vanilla overworld feature plains has (ores, lakes, geodes, springs, dungeons), the vanilla
carvers, plains animals and monsters, and is in `#minecraft:is_overworld`, so the existing Mnemolith modifiers
(memory mobs, archival veins, mute pockets) and other mods' overworld modifiers reach it. Structure tags: mineshaft,
standard ruined portal, stronghold bias and Mnemolith's memory field.

Effects: pale lavender grass and foliage, a pale violet fog and sky, grey-blue water, and rare `imprint_shimmer`
ambient motes.

## Content

| Id | RU / EN | Notes |
| --- | --- | --- |
| `hollow_turf` | Выцветший дёрн / Faded Turf | Shovel. Supports plants (`#minecraft:substrate_overworld`). Drops dirt, itself with Silk Touch. Does not spread. |
| `hollowstone` | Пустокамень / Hollowstone | Pickaxe, like stone. Drops itself. |
| `hollowstone_bricks`, `_stairs`, `_slab`, `_wall` | Кирпичи из пустокамня … | Crafting and stonecutter. |
| `recollite_ore` | Реколлитовая руда / Recollite Ore | Stone pickaxe. 1 to 2 shards, Fortune, 2 to 5 XP; Silk Touch drops the ore. Smelts to a shard. |
| `recollite_shard` | Осколок реколлита / Recollite Shard | A pale memory crystal. Counts as a memory crystal (`#mnemolith:memory_crystals`) in the field guide, echo slip, catalog fragment and memory bolt recipes, beside the amethyst shard. |
| `recollite_block` | Реколлитовый блок / Block of Recollite | 9 shards. Light level 6. |
| `forget_me_not` | Незабудка / Forget-me-not | Flower: light blue dye, a short Night Vision in suspicious stew. |

All art is 32x from the `tools/art` pipeline, in the existing palette (pale, amethyst, glass and bone ramps).

## Memory flickers

A flicker is a translucent figure that plays a few seconds of something that happened nearby, then fades.

- **Server picks, client draws.** Once a second (`tickCount % 20`), for each Overworld player standing in Memory
  Hollows, with chance `0.25 × hollowFlickerDensity` (capped at 1), the server picks a spot 6 to 20 blocks away whose surface is also
  in the biome and whose chunk is loaded. No scan: one biome read for the player, one for the spot, one height read.
- **From real data.** The spot's chunk imprints decide the scene: a fall imprint plays a stumble and fall, death or
  boss a kneeling figure, fire or lightning a flaring figure, build or redstone a figure at work, path or player
  a walk. A chunk with no imprint plays a quiet walk. The tag also tints the figure.
- **Payload.** `HollowFlickerPayload(pos, yaw, scene, tag)` goes to players within 48 blocks of the spot, so friends
  see the same flicker. The client keeps at most 6, each 80 ticks, drawn like the recall ghosts (`debugQuads`, no
  textures, no entity). Footstep motes respect `particleDensity`.
- **Limits.** At most one flicker per player per 3 seconds (60 ticks); at density 1 that averages about one every 7 seconds. Nothing is written to the world.
- **Config.** `hollowFlickerDensity` (common, 0.0 to 4.0, default 1.0; 0 turns flickers off on the server) and
  `hollowFlickers` (client, on by default: hides them locally).

## Progression and discovery

- Advancements: **Where the World Forgets** (enter Memory Hollows, child of the root) and **Pale Crystal**
  (get a recollite shard). Recipe advancements unlock the hollowstone family on picking up hollowstone, the recollite
  recipes on picking up a shard, the flower dye on picking up the flower.
- JEI shows every crafting, smelting, blasting and stonecutting recipe (vanilla categories) and an information page
  on the ore, shard, turf and flower that says where to find them.
- Field guide: a new page `hollows` after `places` (37 pages).

## Performance

- Worldgen: two hook checks per biome sample (an identity-set lookup for hosts, then a few long comparisons), only
  in multi-noise sources. The ground pass is about 256 biome reads and up to about 4 000 block writes per biome chunk,
  once.
- Runtime: once a second per player, a biome read; the payload is about 20 bytes. Measured MSPT next to the biome
  with flickers at density 1 and 4 against density 0 is in the QA checklist.

## Tests

- `/mnemolith hollowsqa` (also a GameTest suite): biome and region registered, host tag bound, a fresh overworld
  multi-noise source lists the biome and the nether one does not, a target point inside the window over a host turns
  into Memory Hollows and one outside stays, a non-host stays, the ground pass converts turf and stone and seeds ore
  in a test column, the remnant places, the ore loot table drops shards, the turf holds a flower, flicker scene
  selection follows the chunk's imprints, density 0 sends nothing, recipes and advancements load, guide page count.
- Deferred to stage 2: a live GameTest that a fake player receives the payload. `flickerPick` covers the picker and
  the in-game run covered delivery and drawing.
- Manual: a fresh normal world, `/locate biome mnemolith:memory_hollows`, screenshots, MSPT.

## Next stages

- Stage 2: a biome structure (a sunken "hollow archive"), recollite tools or a recollite lens upgrade, catching a
  flicker into a slip, biome music.
