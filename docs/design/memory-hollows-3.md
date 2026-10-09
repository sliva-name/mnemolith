# Memory Hollows, stage 3

Stage 2 ([memory-hollows-2.md](memory-hollows-2.md)) added the sunken archive, catching flickers, the recollite lens
and the biome's sound. Stage 3 gives the hollows something that lives there, a use for the archive's lectern and a
recollite upgrade for the needle.

## The faded

- **What.** `mnemolith:faded`, a `Monster` (0.6×1.8, 16 health, 3 attack, speed 0.23, 5 xp). Someone the hollows
  half-forgot: a thin grey-lilac figure with a hollow face, a frayed veil and fraying legs, drawn translucent
  (alpha about 0.47, breathing ±0.08). Model `FadedModel` (64×64 UV, body / head / veil / arms / legs, a recollite
  splinter in the chest), texture from `tools/art/mnart/hollows.py` (`faded`).
- **Copies flickers.** When a flicker is shown (`HollowFlickers.send`), every idle faded within 16 blocks remembers
  its tag and walks to the spot (`MimicGoal`); there it stands and plays the same scene (walk, work, kneel, fall,
  flare) for 3 s. That is the tell: a "flicker" that stays solid and walks away afterwards is a faded.
- **Takes a catch as theft.** Catching a flicker within 16 blocks of a faded provokes it at the catcher (15 s of
  anger, then it forgets unless hurt again). Creative and spectator players are left alone. Otherwise a faded is
  neutral-ish: it attacks a player only when provoked or hurt (`HurtByTargetGoal`; no nearest-player targeting), so
  the hollows stay a calm place to walk through.
- **Seen through the lens.** Unseen, a player's hits do half damage (×0.5). A player who raises a lens (either kind)
  and looks at a faded within 12 blocks with line of sight reveals it for 5 s: glowing, drawn more opaque, and
  player damage ×1.5. Checked every 5 ticks per faded, cheap (`isFocusing` is tested first). Advancement
  *Seen Through* on the first reveal.
- **Hit.** Slowness I for 3 s ("memory drag").
- **Loot.** `entities/faded`: 0–1 recollite shard (looting +0–1, player kills only), a forget-me-not at 25%, 0–2
  string. Killed by a player, it drops a slip of the tag it last copied at 50%; a faded that never copied anything
  drops no slip. Slips stay rare: a faded only copies flickers, and flickers only show imprints the chunk holds.
- **Sounds.** Ambient reuses the biome's mood breath at a higher pitch; hurt and death are synthesised (glassy
  crack and falling shimmer, `tools/art/generate_sounds.py --faded`). Subtitles for all.

## Spawning: Memory Hollows only

- **Own spawner, not the biome spawn list.** `FadedSpawner` runs every 100 ticks. For each player (not spectators),
  `armoryMobs.fadedSpawnWeight` out of 100 rolls (default 25; 0 turns it off) pick one spot 20 to 40 blocks away at
  the surface (`MOTION_BLOCKING_NO_LEAVES`). A faded spawns there only if `MobSpawns.fadedSpotOk` holds (the biome
  is Memory Hollows, the spot is at the open surface, fewer than 3 faded within 32 blocks), the ground takes an
  `ON_GROUND` spawn, the box is free and no player is within 16 blocks. Peaceful, `spawn_mobs` and `spawn_monsters`
  turn it off. Any light, so they are there by day too (the hollows are a dim place, not a dark one), but never many.
- **Why not `neoforge:add_spawns`.** The first build used a biome modifier on `memory_hollows` (weight 30) and the
  vanilla natural spawner. On the normal test world it gave no faded on the turf in 4 minutes at the heart of a
  patch, and the one faded found was at y = -38: biomes are 3D, so the caves under a hollow are Memory Hollows too,
  and the natural spawner picks a random height in the column (the caves win almost every roll) and stops at the
  monster cap, which the zombies and skeletons in those caves fill (30 to 50 undead within 128 blocks at the time).
  The modifier is gone; the spawn placement (`ON_GROUND`, any light, `MobSpawns.allowFaded`) stays registered, so a
  datapack that adds faded to a spawn list still gets the hollows, surface and cap rules.
- Spawned faded are ordinary monsters: they despawn when no player is near, as monsters do. Not in peaceful.
- Spawn egg (pale lilac with a recollite band) and `/mnemolith spawn faded`.

## Lectern replay

- Right-click any vanilla lectern with an imprint slip (in the archive's apse, or one you place): the lectern reads
  it aloud. A flicker of that slip's scene plays in front of the lectern, facing it, sent to players in range like
  a normal flicker; a page-turn chime plays; the action bar says what the slip was and what the lectern's chunk
  holds ("The lectern reads: Path. This chunk remembers: Path ×2, Trade.", or "nothing").
- The slip is kept, and the lectern keeps its book: the event is cancelled before vanilla handles it, only when
  holding a slip.
- A replay is a reading, not a memory: it is not registered as a catchable flicker, so lecterns are not a slip
  fountain. Faded ignore it too (only `send` calls `Faded.onFlicker`), so a lectern cannot be used to farm them.
- 5 s cooldown per lectern ("The lectern is still reading.").
- Advancement *Read Aloud*, child of *Under the Turf*.

## Recollite needle

- `mnemolith:recollite_needle` (`RecolliteNeedleItem`, an `ExtractionNeedleItem`): extracts like the plain needle
  and has twice its durability. It catches flickers **without a lens**, up to 7 blocks. With a lens the reach is
  the best of what is held (chronicle lens 5, recollite lens 9), so the recollite lens stays the long-range option.
- Recipe: the extraction needle in the middle, four recollite shards around it (a plus). Unlocked with the first
  shard. Advancement *A Steadier Hand*.
- Sprite: the needle with a recollite tip and band (`items.extraction_needle` takes the tip as a parameter; the
  default output is pixel-identical).

## Small fixes

- The guide's reference page shows the recollite lens (18th slot) and its crafts text lists the recollite lens and
  needle recipes.
- JEI info pages for the recollite needle and for the lectern (what a slip does on it).
- Tooltip hint on the recollite needle.

## Field guide

- New page `faded` after `sunken` (39 pages): a kneeling flicker and a faded copying it, a revealed faded, lens →
  glowing; slip → lectern → replay; the recollite needle and its reach ring.

## Tests

- `/mnemolith hollows3qa` (also a GameTest suite, 19 checks): the faded is registered as a monster and not
  peaceful; its loot table loads; the spawn spot is refused outside the hollows and accepted after `fillbiome`; three
  faded nearby close the spot and it opens again when they go; a faded copies a trade flicker 5 blocks away; a catch
  provokes it at the catcher; unseen a 4-damage hit takes 2, revealed it takes 6; a raised lens looking at a faded
  sees it, a lowered lens or a turned back does not; out of 12 player kills of a faded that copied fire some, not
  all, drop a fire slip and none drop another slip; a lectern plays a slip (replay counted, nothing catchable even
  with a recollite lens, slip kept), refuses within the cooldown, ignores non-lecterns and non-slips, and describes
  its chunk ("×2"); the recollite needle's own `use` catches a flicker at 6 blocks without a lens, misses at 8 and
  catches at 8 with a recollite lens, and the plain needle still catches nothing without a lens; the recipe matches;
  the needle catches unaided and has double durability; the four sounds are registered; the advancements load; the
  guide page sits after `sunken`.
- Manual: see the stage 3 section of [qa-checklist.md](../qa-checklist.md) (normal world, screenshots, spawn counts
  and MSPT).

## Next stages

- Hollows-specific imprint tags (a "forgotten" tag only the hollows record), a faded variant in sunken archives that
  guards the chest, a recollite pickaxe that reads the chunk while mining.
