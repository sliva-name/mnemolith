# Embers of memory (the Nether line)

Status: stage 1 implemented on `feature/embers-1`; stages 2 and 3 planned.

## Why

The Nether is the thinnest part of the mod. It has:
- one structure (the ashen archive);
- three Overworld mobs reused through a biome modifier;
- not a word in the README.

Nothing there is the Nether's own, and nothing in it gives a reason to go back. At the same time, the memory loop has a
gap at home: pressure is only a cost. The tools to bring a loud base down are slow and indirect: mute stones, a
forgotten graft, vaults that leak.

The lore joins the two. The Nether burns. A memory that falls into it does not fade like one in a hollow; it smoulders.
- **Stage 1** gives the Nether a creature of its own: a memory burning down to cinders. It drops what is left: a
  memory ember.
- **Stage 2** takes the embers home, where a brazier burns away a loud chunk's memories. That turns the Nether into
  the supply line for the cure to pressure.
- **Stage 3** adds server options, multiplayer checks and advancements.

## The line, in three stages

1. **Stage 1, the cinderkin** (this PR). A new Nether mob and the memory ember.
2. **Stage 2, the ember brazier.** A block fed with embers. While lit, it burns the loudest imprint of its chunk
   every few seconds, one ember for every few imprints, and writes nothing. Pressure finally has an active sink, paid
   for in the Nether.
3. **Stage 3, servers and progression:**
   - server options (cinderkin spawn weight, brazier rate);
   - two advancements;
   - a two-player live test (a brazier shared in a base);
   - guide and README sections.

## Stage 1 rules

### The cinderkin (`cinderkin`)

- **Where:**
  - Natural spawns in every Nether biome (`#minecraft:is_nether`), weight 10, alone or in pairs.
  - Any light level, on solid ground, never in the Overworld or the End (the placement check refuses other dimensions).
  - Debug summon: `/mnemolith spawn cinderkin`, plus a spawn egg.
- **Body:**
  - A figure of charcoal and glowing cracks on the faded's frame (FadedModel, same proportions, its own texture).
  - Drawn opaque and full-bright: it glows in the dark.
  - 24 health, armour 2, attack 4, speed 0.26, fire immune.
- **Fighting:**
  - Hostile to players.
  - A hit sets the target on fire for 4 seconds.
- **Cold puts it out** (the counterplay, from its nature as a burning memory):
  - A snowball deals 5 damage to it (vanilla does that to blazes).
  - Water hurts it, like an enderman or a blaze (`isSensitiveToWater`).
- **Death:**
  - In the Nether it leaves a fire imprint where it falls.
  - Killed by a player, it drops 0–2 memory embers (+1 per Looting level).

### The memory ember (`memory_ember`)

- Stacks to 64.
- In stage 1 it is a crafting material and fuel: it burns in a furnace for 1600 ticks (like coal).
- Stage 2 gives it its real use.

## QA

- `/mnemolith emberqa` checks every rule above.
- Live test `cinderkin_burns_player`: a real player is hit by a cinderkin and catches fire; a snowball the player
  throws for real deals its cold damage.
