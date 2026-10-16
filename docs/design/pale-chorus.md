# The Pale Chorus (the End line)

Status: design note (this PR); stage 1 next.

## Why

The End is the mod's emptiest dimension now that the Nether has its line. It holds one structure (the mute library)
and borrowed mobs (archivists and kin witnesses), and nothing about memory there that you can meet in a fight.

The mute library already says what the End is to this mod: the place where memory goes quiet. The Pale Chorus gives
that quiet a voice that sings nothing. Its singers are the **hushlings**, small pale figures that thin memory: they
eat the quietest imprints around them and hush whoever they touch. The line then turns their filament into a tool.

Two parts of the mod meet here:
- **silence** (mute stones, the mute library, the silence imprint);
- **pressure.** The ember brazier burns the *loudest* memory away. A hushling thins the *quietest*, so the two
  read as a pair.

## The line

1. **Stage 1, the hushling.** A new End-only mob and the hush filament.
2. **Stage 2, the filament's use.** A block made with filament (working name *hush loom*). It lets a player choose what a chunk keeps:
   it holds back one imprint tag in its chunk from being thinned or burned, the counterpart to the selective mute
   stone, which only stops writes. The exact rules come in the stage 2 PR.
3. **Stage 3, servers and progression:**
   - server options (hushling spawns, the hush length, the thinning interval);
   - two advancements;
   - a two-player live test;
   - README and guide sections.

Each stage is its own PR (and may be split smaller), merged when its CI and the local runs are green.

## Stage 1 rules

### The hushling (`hushling`)

- **Where:**
  - Natural spawns only in End biomes (`#minecraft:is_end`), weight 6, alone or in pairs.
  - The placement check refuses every other dimension.
  - Debug summon: `/mnemolith spawn hushling`, plus a spawn egg.
- **Body:**
  - The faded's frame (FadedModel) at 0.8 scale, with its own texture: pale bone-white cloth and a dark open mouth.
  - Not fire immune.
  - 20 health, attack 3, speed 0.3.
- **Thinning:** every 10 s (`THIN_PERIOD`) a hushling removes the *quietest* imprint of the chunk it stands in, and
  writes nothing. It is the opposite of the brazier, which burns the loudest.
- **The silence-flavoured hit:**
  - A player it hits is **hushed** for 6 s (`HUSH_TICKS`), with Slowness I.
  - While hushed, nothing the player does writes an imprint. Writes made in the player's name (`ImprintWriter`
    with that player's id) are dropped.
  - Hushed players see a short overlay line.
- **The counterplay rule: sound breaks the hush.**
  - A note block played within 12 blocks (`NOTE_RANGE`) *shatters* every hushling in range for 10 s.
  - A shattered hushling cannot thin, cannot hush, glows, and takes double damage.
  - Bring a note block, or build them into an End base.
- **Death:**
  - In the End it writes a **silence** imprint where it falls.
  - Killed by a player, it drops 0–2 hush filament (+1 per Looting level).
- **Sounds:** the faded's voice pitched up, and a muted amethyst chime when it hushes someone.

### Hush filament (`hush_filament`)

Stacks to 64. In stage 1 it is a material only. Stage 2 gives it its use.

## QA

- `/mnemolith hushqa` checks the stage 1 rules:
  - item and entity data;
  - thinning removes the quietest imprint and only that one;
  - the hit hushes a player, and a hushed player's writes are dropped until the hush ends;
  - a played note block shatters hushlings in range and not beyond, and a shattered one neither thins nor hushes and
    takes double damage;
  - the silence imprint in the End and not elsewhere;
  - loot, spawn modifier and the guide page.
- Live test: a hushling with its AI on finds a real player, hits them, and the player is hushed.
