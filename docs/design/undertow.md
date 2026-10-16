# Undertow: memory angling

Status: stages 1 and 2 done (memory angling, the tide lure); stage 3 next.

## Why

Every removal of memory so far is a block or a mob: the ember brazier burns the loudest imprint, hushlings thin the
quietest, the hush loom chooses what stays. None of them is something a player *does* with their own hands, and none
lives in water. Fishing is the one quiet, patient activity vanilla already has, and it fits memory: what sinks can be
fished back up. Undertow makes the fishing rod a slow, rewarding way to take memory out of a chunk, and gives the
taken memory back as an imprint slip that the rest of the mod already uses (selective mute stones, grafts, the
lectern).

## The line

1. **Stage 1, angling.** Fishing in a chunk that remembers can bring up a memory alongside the catch.
2. **Stage 2, the tide lure.** An off-hand lure that changes what the line pulls and how often.
3. **Stage 3, servers and progression:** server options (angling chance, lure chance), two advancements, a
   two-player live test.

## Stage 1 rules

- **When:** a player reels in a catch (NeoForge `ItemFishedEvent`, not cancelled) and the bobber is in water.
- **Where:** the chunk under the bobber. It must hold at least one imprint that no hush loom keeps.
- **Chance:** 30% per catch, plus 10% per level of Luck of the Sea on the rod (at most 60%).
- **What:** the chunk's **oldest** imprint that no hush loom keeps is taken out of the chunk and comes up as an imprint
  slip of that tag (the usual `ImprintSlips.of(tag, pos)`), flung to the player like a second catch. Pressure is
  recomputed. Nothing is written in its place.
- **Not:** treasure, junk and fish are unchanged; a cancelled catch, a hook on land or lava, and an empty chunk bring
  up nothing. Fake players can angle (automation is allowed; it is still one catch at a time).
- **Feedback:** a bubble column of particles at the bobber and a soft splash; the slip's name shows the tag.
- **Guide:** one page, "Undertow".

## QA

- `/mnemolith anglingqa`: a real fishing hook in water owned by a fake player; the event handler with a forced roll
  takes the oldest imprint and flings one slip of its tag; a forced miss takes nothing; an empty chunk and a hook on
  land take nothing; a hush-loom-kept tag is skipped; Luck of the Sea raises the chance and caps at 60%; guide page.
- Live test: a real player with a rod and a hook in water; the catch event on the bus brings up a slip of the
  chunk's oldest imprint, which reaches the player.

## Stage 2 rules

### The tide lure (`tide_lure`)

- **Recipe:** string, a recollite shard and three prismarine shards (` S  / PRP /  P `). Does not stack; 32 uses.
- **Use:** held in the **off hand** while fishing (rod in the main hand). In the main hand it does nothing.
- **Effect:** the chance rises by 20% (Luck of the Sea still counts), up to 80%; and the line pulls the chunk's
  **loudest** imprint that no hush loom keeps, instead of its oldest. So the bare rod is slow housekeeping and the
  lure is a targeted way to take the worst memory out.
- **Wear:** one use per memory landed; a catch that brings no memory does not wear it. At 0 it breaks.
- **Guide:** a paragraph on the Undertow page.

### QA (stage 2)

- `/mnemolith lureqa`: item data and recipe; the chance with and without the lure and its cap; a lured angle takes the
  loudest and wears the lure by one; an empty chunk does not wear it; a lure in the main hand changes nothing; a worn
  lure breaks; seeded rolls wear it once per hit.
- Live test `lured_angler_lands_loudest`: a real player with a lure; the loudest imprint comes up and reaches the
  inventory, and the lure wears by one.
