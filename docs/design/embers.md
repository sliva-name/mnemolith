# Embers of memory (the Nether line)

Status: done on main (stage 1 PR #83, stage 2 PR #84, stage 3 PR #85).

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
- Stage 2 gives it its real use: it feeds the ember brazier.

## Stage 2 rules

### The ember brazier (`ember_brazier`)

- **Recipe (shaped):** `I I` / `IEI` / `BBB`, where I is an iron ingot, E a memory ember and B polished blackstone.
  The recipe unlocks when you pick up a memory ember. Mined with a pickaxe; it drops itself.
- **Feeding:** use a memory ember on it. One ember adds **4 charges** (`CHARGE_PER_EMBER`), and it holds up to
  **16** (`MAX_CHARGE`). An ember that would overflow is refused with an overlay line, and nothing is spent. Fed, it
  lights (light 13, flames and smoke on top). There is no other fuel: embers come only from cinderkin.
- **Burning:** every **100 ticks** (5 s, `BURN_PERIOD`) a lit brazier takes the loudest imprint of its own chunk (the
  same pick as the Unremembered's feeding: `removeHighest`) and spends one charge. It writes nothing in its place, and
  the chunk's pressure is recomputed and saved.
- **Nothing to burn:** a chunk with no imprints spends nothing; the brazier stays lit and tries again next period.
- **Going cold:** the last charge puts it out (unlit model, no light), and it stops scheduling ticks.
- **Comparator:** the signal is the charge left (0–16, capped at 15).
- **Why it is balanced:** a cinderkin drops 0–2 embers (about one on average), so one kill burns about 4 imprints.
  A busy base writes far more than that, so the brazier keeps a room or a farm calm rather than silencing a whole
  town. Mute stones still stop writing; the brazier is the only block that removes what was already written.

### Not in stage 2

No server options or advancements (stage 3).

## Stage 3 rules

### Server options (`config/mnemolith-server.toml`, section `embers`)

| Option | Default | Range | Effect |
| --- | --- | --- | --- |
| `cinderkinSpawns` | true | | Natural cinderkin spawns in the Nether. Off, the spawn rule refuses natural spawns; spawn eggs and `/mnemolith spawn cinderkin` still work, and cinderkin already out stay. |
| `emberBrazierBurnTicks` | 100 | 20–1200 | Ticks between burns. A changed value applies from the next scheduled burn. |
| `emberBrazierChargePerEmber` | 4 | 1–16 | Burns one ember gives. The brazier still holds 16 at most, and an ember that would overflow it is refused. |

The numbers and their reasons are in [balance.md](../balance.md#embers).

### Advancements

- **Embers of Memory** (`memory_ember`, under the root): pick up a memory ember.
- **Burn It Away** (`ember_brazier_kindled`, under Embers of Memory): feed an ember to a brazier by hand. Awarded by
  the block to the player who fed it.

### Multiplayer

The brazier keeps no owner: whoever feeds it adds to the same charge, and anyone may feed a brazier someone else
built. The overflow check is per ember, so two players feeding at once cannot push it past 16.

## QA

- `/mnemolith emberqa` checks every rule above.
- Live test `cinderkin_burns_player`: a cinderkin with its AI on finds a real player by itself, and its hit sets
  the player on fire. (Snowball damage is checked in `emberqa` with a real snowball entity as the damage source.)
- `/mnemolith ember2qa` checks the brazier: recipe, loot, feeding by hand and the full refusal, burning the loudest
  imprint and writing nothing, an empty chunk, going cold, light and comparator.
- Live test `brazier_burns_down`: a real player feeds an ember by hand (`gameMode.useItemOn`), and the brazier's own
  scheduled ticks burn the two imprints written into its chunk.
- `/mnemolith ember3qa` checks the stage 3 options (defaults, ranges, lang), the spawn rule with spawns off (natural
  refused, egg allowed, never in the Overworld), the brazier reading its charge and period from the options, and the
  two advancements.
- Live test `brazier_shared`: two real players take turns feeding one brazier by hand; the charge adds up to 16, the
  refused ember is kept, and both earn Burn It Away.
