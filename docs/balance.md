# Balance

Phase 9 tunes the defaults that already existed. Band thresholds stay 20 / 50 / 80, and the soft cap stays 100, so the lens bands mean the same thing. What changed is how fast ordinary play reaches them, how repeats are scored, and who is allowed to show up.

## Target curve

| Play | Pressure | Band |
| --- | --- | --- |
| Walking, or a few placed blocks | under 20 | Calm |
| One death (death + player), or one creeper | about 31, or 24 | Saturated |
| A death and an explosion together | about 55 | Overloaded |
| A failed compose on a quiet chunk | +18, still under 20 | Calm. No replicant |
| A failed compose on an overloaded chunk | about 73 | Overloaded. A replicant is asked |
| A second failure after that | about 91 | Fracture |

A vein adds at most 6, so it never changes the band by itself. Mute stone writes 8 (silence) and then stops further writes in that chunk. Instability cools by 1 every 200 ticks (10 seconds) while a player stands in the chunk, so a spike of 18 is gone in about three minutes. Build, redstone, and path imprints older than 6000 ticks (5 minutes) fade one per pulse. Deaths, explosions, falls, fire, silence, and player imprints stay until extracted.

The first imprint is a path mark after about 12 blocks of walking, or the first block you place once the chunk's 4 second build pause has elapsed. A needle is amethyst, iron, and a stick. A reel is copper, paper, and a crafting table. An observatory chest always contains a lens or a needle. That is the 10–20 minute early loop: walk, die or blow something up, extract, compose.

## Before and after

| Default | Before | After |
| --- | --- | --- |
| Tag weights (fire, fall, death, build, explosion, silence, player, redstone, path) | 6, 8, 12, 3, 10, 5, 4, 3, 2 | 4, 5, 9, 2, 8, 4, 2, 2, 1 |
| One imprint, intensity × weight (same order) | 12, 16, 36, 3, 30, 10, 8, 3, 2 | 8, 10, 27, 2, 24, 8, 4, 2, 1 |
| Repeated tags in the score | Every copy is full | Strongest copy is full. Up to 3 more add a quarter (minimum 1). The rest are stored and add 0 |
| `gameplay.writeDebounceTicks` | 40 | 80 |
| `gameplay.failurePressureSpike` | 8 | 18 |
| `gameplay.extractionDurabilityCost` | 1 | 2 |
| `gameplay.extractionCooldownTicks` | none | 20 |
| `gameplay.instabilityDecay` | none (instability only rose) | 1 |
| `gameplay.instabilityDecayTicks` | none | 200 |
| `gameplay.quietFadeTicks` | none | 6000 |
| Saturated / overloaded / fracture / soft cap | 20 / 50 / 80 / 100 | unchanged |
| Unrecorded / fire trail / landing burst | 200 / 160 / 600 ticks | 300 / 240 / 400 ticks |
| Fire trail speed bonus | +0.08 | +0.04 |
| Landing burst damage multiplier | 0.2 | 0.35 |
| Compose cooldown | none | still none. One slip and the spike are the limiter |
| Failed compose spawns a replicant | always | only if the chunk is then overloaded or fractured |
| Strider damage / weight / min pressure | 4 / 50 / 20 | 4 / 35 / 20 |
| Strider charge telegraph | 40 ticks | 45 ticks. The charge step is unchanged |
| Archivist damage / weight / min pressure | 2 / 40 / 20 | 2 / 25 / 50 |
| Archivist steal cooldown | 200 ticks | 300 ticks |
| Replicant damage / weight / min pressure | 5 / 20 / 80 | 5 / 12 / 80 |
| Replicant telegraph | 40 ticks | 60 ticks |
| Biome spawn weights (strider, archivist, replicant) | 40 / 30 / 8 | unchanged. The config roll and the pressure gate are the knobs |
| `worldGen.archivalVeinChance` | 12 | 8 |
| `worldGen.archivalBleed` / cap | 1 / 6 | unchanged |
| `worldGen.mutePocketChance` | 2 | 4 |
| Observatory spacing / separation | 40 / 16 | 32 / 12 in the structure set. `worldGen.structureSpacing` documents 32 |
| Observatory chest | 3 rolls from one pool. A lens was 2 of 31 | 1 guaranteed lens or needle, then 2 teaching rolls |
| Mute pocket chest | tablet or silence slip | tablet, silence slip, or a rarer needle |
| Archivist loot | catalog every kill, husk at 35% | catalog, paper, or ink; husk at 25% |
| Strider / replicant loot tables | empty (code still drops a path slip, or an unstable slip and sometimes an explosion slip) | the same code drops, plus paper, copper or gunpowder, and amethyst |

## Why

Ordinary building used to be cheap per block and still unbounded: every break wrote a full build imprint and a full player imprint, with only an 8-imprint cap and a 2 second pause. Four places now score about 12 and stay calm, and the pause is 4 seconds. A death farm in one chunk saturates (the fourth death is the last one that adds pressure) and does not fracture.

Loud events were large enough that one death (36) jumped to saturated and a death plus a creeper could approach fracture before any compose. They are now 27 and 24. Two of them overload the chunk. That is the moment archivists are allowed to spawn. Striders remain the saturated-tier threat, and they still refuse muted chunks. Replicants stay on the fracture band for natural spawns. A careless compose on a quiet chunk spends a slip and adds instability. It does not call a replicant.

Formula times are long enough to use and short enough to recast. Fire trail no longer adds most of a player's walk speed. Landing burst still saves a hard fall, and it no longer lasts half a minute or reduces the hit to a fifth. The needle's 64 durability now covers 32 extracts, with a one second rest so a click does not empty a chunk.

Veins were common enough to tint a lot of stone and rare enough that the bleed never mattered. 8% keeps them as a mining find. Mute pockets at 2% were easy to miss for a whole session; 4% makes a silent room a place you can plan around. Observatories moved from 40/16 to 32/12 so a first reel is more likely inside the early loop, and the chest teaches the tools instead of sometimes hiding both of them.

Catalog unlock is unchanged: a tag is learned when you extract it or put it in the reel, and a formula is learned when that compose succeeds. Hints still count unread patterns. They do not list them.

Server config defaults are unchanged. Storms are still not started. `server.logPressureChanges` logs band changes other than fracture. Cooling and write strengths live in the common gameplay section.

## Memory grafts

One imprint slip grafted into an echo gives charges; a graft holds two slips' worth. `echoGraftChargeScale` multiplies every number in the charge column.

| Temper (slip) | Charges per slip | One charge buys | Rough value of one slip |
| --- | --- | --- | --- |
| Hushed (silence) | 12 | one swallowed work imprint (with the defaults one imprint per 20 work actions, +3 instability) | ~240 quiet work actions, ~36 instability not written |
| Grave (death) | 24 | one hostile hit taken | a night of decoy duty next to a farm |
| Kindled (fire) | 32 | one ore smelted (own or a neighbour's) | 32 ingots without fuel or a furnace |
| Plunging (fall) | 24 | one long drop (>3.5) or one floor dug over a long drop | a 12-deep shaft in a few steps |
| Volatile (explosion) | 48 | one dug block at ×0.55 time | ~48 blocks of fast digging, then explosion residue (weight 8) per work imprint |

Why these numbers: silence is the cheapest slip to get (a mute stone writes one) and the strongest effect (it hides pressure), so it gets the fewest charges. Death and fall slips need a real risk. Fire is common in the Nether, but ingots are a real economy, so one slip is capped at 32. Explosion slips are rarest and their residue is a cost of its own, so they get the most charges. A worn graft (under half a slip) is gone when it leaves the echo, so topping up is the way to keep one.
